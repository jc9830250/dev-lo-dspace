/*
 * HandleServlet.java
 *
 * Version: $Revision: 2768 $
 *
 * Date: $Date: 2008-02-29 07:29:19 -0800 (Fri, 29 Feb 2008) $
 *
 * Copyright (c) 2002-2005, Hewlett-Packard Company and Massachusetts
 * Institute of Technology.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * - Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * - Neither the name of the Hewlett-Packard Company nor the name of the
 * Massachusetts Institute of Technology nor the names of their
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
package org.dspace.app.webui.servlet;

import org.apache.log4j.Logger;
import org.dspace.app.webui.util.Authenticate;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.app.webui.util.UIUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.content.*;
import org.dspace.content.crosswalk.CrosswalkException;
import org.dspace.content.crosswalk.DisseminationCrosswalk;
import org.dspace.core.*;
import org.dspace.eperson.EPerson;
import org.dspace.eperson.Group;
import org.dspace.eperson.Subscribe;
import org.dspace.handle.HandleManager;
import org.dspace.plugin.CollectionHomeProcessor;
import org.dspace.plugin.CommunityHomeProcessor;
import org.jdom.Element;
import org.jdom.output.XMLOutputter;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.List;

/**
 * Servlet for handling requests within a community or collection. The Handle is
 * extracted from the URL, e.g: <code>/community/1721.1/1234</code>. If there
 * is anything after the Handle, the request is forwarded to the appropriate
 * servlet. For example:
 * <P>
 * <code>/community/1721.1/1234/simple-search</code>
 * <P>
 * would be forwarded to <code>/simple-search</code>. If there is nothing
 * after the Handle, the community or collection home page is shown.
 * 
 * @author Robert Tansley
 * @version $Revision: 2768 $
 */
public class HandleServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(DSpaceServlet.class);

    /** For obtaining &lt;meta&gt; elements to put in the &lt;head&gt; */
    private DisseminationCrosswalk xHTMLHeadCrosswalk;

    public HandleServlet()
    {
        super();
        xHTMLHeadCrosswalk = (DisseminationCrosswalk) PluginManager
                .getNamedPlugin(DisseminationCrosswalk.class, "XHTML_HEAD_ITEM");
    }   

    protected void doDSGet(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        String handle = null;
        String extraPathInfo = null;
        DSpaceObject dso = null;

        // Original path info, of the form "1721.x/1234"
        // or "1721.x/1234/extra/stuff"
        String path = request.getPathInfo();

        if (path != null)
        {
            // substring(1) is to remove initial '/'
            path = path.substring(1);

            try
            {
                // Extract the Handle
                int firstSlash = path.indexOf('/');
                int secondSlash = path.indexOf('/', firstSlash + 1);

                if (secondSlash != -1)
                {
                    // We have extra path info
                    handle = path.substring(0, secondSlash);
                    extraPathInfo = path.substring(secondSlash);
                }
                else
                {
                    // The path is just the Handle
                    handle = path;
                }
            }
            catch (NumberFormatException nfe)
            {
                // Leave handle as null
            }
        }

        // Find out what the handle relates to
        if (handle != null)
        {
            dso = HandleManager.resolveToObject(context, handle);
        }

        if (dso == null)
        {
            log.info(LogManager
                    .getHeader(context, "invalid_id", "path=" + path));
            JSPManager.showInvalidIDError(request, response, path, -1);

            return;
        }

        // OK, we have a valid Handle. What is it?
        if (dso.getType() == Constants.ITEM)
        {
            Item item = (Item) dso;

            // Only use last-modified if this is an anonymous access
            // - caching content that may be generated under authorisation
            //   is a security problem
            if (context.getCurrentUser() == null)
            {
                response.setDateHeader("Last-Modified", item
                        .getLastModified().getTime());

                // Check for if-modified-since header
                long modSince = request.getDateHeader("If-Modified-Since");

                if (modSince != -1 && item.getLastModified().getTime() < modSince)
                {
                    // Item has not been modified since requested date,
                    // hence bitstream has not; return 304
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                }
                else
                {
                    // Display the item page
                    displayItem(context, request, response, item, handle);
                }
            }
            else
            {
                // Display the item page
                displayItem(context, request, response, item, handle);
            }
        }
        else if (dso.getType() == Constants.COLLECTION)
        {
            Collection c = (Collection) dso;

            // Store collection location in request
            request.setAttribute("dspace.collection", c);

            /*
             * Find the "parent" community the collection, mainly for
             * "breadcrumbs" FIXME: At the moment, just grab the first community
             * the collection is in. This should probably be more context
             * sensitive when we have multiple inclusion.
             */
            Community[] parents = c.getCommunities();
            request.setAttribute("dspace.community", parents[0]);

            /*
             * Find all the "parent" communities for the collection for
             * "breadcrumbs"
             */
            request.setAttribute("dspace.communities", getParents(parents[0],
                    true));

            // home page, or forward to another page?
            if ((extraPathInfo == null) || (extraPathInfo.equals("/")))
            {
                collectionHome(context, request, response, parents[0], c);
            }
            else
            {
                // Forward to another servlet
                request.getRequestDispatcher(extraPathInfo).forward(request,
                        response);
            }
        }
        else if (dso.getType() == Constants.COMMUNITY)
        {
            Community c = (Community) dso;

            // Store collection location in request
            request.setAttribute("dspace.community", c);

            /*
             * Find all the "parent" communities for the community
             */
            request.setAttribute("dspace.communities", getParents(c, false));

            // home page, or forward to another page?
            if ((extraPathInfo == null) || (extraPathInfo.equals("/")))
            {
                communityHome(context, request, response, c);
            }
            else
            {
                // Forward to another servlet
                request.getRequestDispatcher(extraPathInfo).forward(request,
                        response);
            }
        }
        else
        {
            // Shouldn't happen. Log and treat as invalid ID
            log.info(LogManager.getHeader(context,
                    "Handle not an item, collection or community", "handle="
                            + handle));
            JSPManager.showInvalidIDError(request, response, path, -1);

            return;
        }
    }

    /**
     * Show an item page
     * 
     * @param context
     *            Context object
     * @param request
     *            the HTTP request
     * @param response
     *            the HTTP response
     * @param item
     *            the item
     * @param handle
     *            the item's handle
     */
    private void displayItem(Context context, HttpServletRequest request,
            HttpServletResponse response, Item item, String handle)
            throws ServletException, IOException, SQLException,
            AuthorizeException
    {
        // Tombstone?
        if (item.isWithdrawn())
        {
            JSPManager.showJSP(request, response, "/tombstone.jsp");

            return;
        }

        // Ensure the user has authorisation
        AuthorizeManager.authorizeAction(context, item, Constants.READ);

        log
                .info(LogManager.getHeader(context, "view_item", "handle="
                        + handle));

        // show edit link
        if (item.canEdit())
        {
            // set a variable to create an edit button
            request.setAttribute("admin_button", new Boolean(true));
        }

        // Get the collections
        Collection[] collections = item.getCollections();

        // For the breadcrumbs, get the first collection and the first community
        // that is in. FIXME: Not multiple-inclusion friendly--should be
        // smarter, context-sensitive
        request.setAttribute("dspace.collection", item.getOwningCollection());

        Community[] comms = item.getOwningCollection().getCommunities();
        request.setAttribute("dspace.community", comms[0]);

        /*
         * Find all the "parent" communities for the collection
         */
        request.setAttribute("dspace.communities", getParents(comms[0], true));

        // Full or simple display?
        boolean displayAll = false;
        String modeParam = request.getParameter("mode");

        if ((modeParam != null) && modeParam.equalsIgnoreCase("full"))
        {
            displayAll = true;
        }

        String headMetadata = "";

        // Produce <meta> elements for header from crosswalk
        try
        {
            List l = xHTMLHeadCrosswalk.disseminateList(item);
            StringWriter sw = new StringWriter();

            XMLOutputter xmlo = new XMLOutputter();
            for (int i = 0; i < l.size(); i++)
            {
                Element e = (Element) l.get(i);
                // FIXME: we unset the Namespace so it's not printed.
                // This is fairly yucky, but means the same crosswalk should
                // work for Manakin as well as the JSP-based UI.
                e.setNamespace(null);
                xmlo.output(e, sw);
            }
            headMetadata = sw.toString();
        }
        catch (CrosswalkException ce)
        {
            throw new ServletException(ce);
        }

        // Enable suggest link or not
        boolean suggestEnable = false;
        if (!ConfigurationManager.getBooleanProperty("webui.suggest.enable"))
        {
            // do nothing, the suggestLink is allready set to false 
        }
        else
        {
            // it is in general enabled
            suggestEnable= true;
            
            // check for the enable only for logged in users option
            if(!ConfigurationManager.getBooleanProperty("webui.suggest.loggedinusers.only"))
            {
                // do nothing, the suggestLink stays as it is
            }
            else
            {
                // check whether there is a logged in user
                suggestEnable = (context.getCurrentUser() == null ? false : true);
            }
        }
        
        // Set attributes and display
        request.setAttribute("suggest.enable", new Boolean(suggestEnable));
        request.setAttribute("display.all", new Boolean(displayAll));
        request.setAttribute("item", item);
        request.setAttribute("collections", collections);
        request.setAttribute("dspace.layout.head", headMetadata);
        
        JSPManager.showJSP(request, response, "/display-item.jsp");
    }

    /**
     * Show a community home page, or deal with button press on home page
     * 
     * @param context
     *            Context object
     * @param request
     *            the HTTP request
     * @param response
     *            the HTTP response
     * @param community
     *            the community
     */
    private void communityHome(Context context, HttpServletRequest request,
            HttpServletResponse response, Community community)
            throws ServletException, IOException, SQLException
    {
        // Handle click on a browse or search button
        if (!handleButton(request, response, community.getHandle()))
        {
            // No button pressed, display community home page
            log.info(LogManager.getHeader(context, "view_community",
                    "community_id=" + community.getID()));

            // Get the collections within the community
            Collection[] collections = community.getCollections();

            // get any subcommunities of the community
            Community[] subcommunities = community.getSubcommunities();

            // perform any necessary pre-processing
            preProcessCommunityHome(context, request, response, community);

            // is the user a COMMUNITY_EDITOR?
            if (community.canEditBoolean())
            {
                // set a variable to create an edit button
                request.setAttribute("editor_button", new Boolean(true));
            }

            // can they add to this community?
            if (AuthorizeManager.authorizeActionBoolean(context, community,
                    Constants.ADD))
            {
                // set a variable to create an edit button
                request.setAttribute("add_button", new Boolean(true));
            }

            // can they remove from this community?
            if (AuthorizeManager.authorizeActionBoolean(context, community,
                    Constants.REMOVE))
            {
                // set a variable to create an edit button
                request.setAttribute("remove_button", new Boolean(true));
            }

            // Forward to community home page
            request.setAttribute("community", community);
            request.setAttribute("collections", collections);
            request.setAttribute("subcommunities", subcommunities);
            JSPManager.showJSP(request, response, "/community-home.jsp");
        }
    }

    private void preProcessCommunityHome(Context context, HttpServletRequest request,
            HttpServletResponse response, Community community)
    	throws ServletException, IOException, SQLException
    {
    	try
    	{
    		CommunityHomeProcessor[] chp = (CommunityHomeProcessor[]) PluginManager.getPluginSequence(CommunityHomeProcessor.class);
    		for (int i = 0; i < chp.length; i++)
    		{
    			chp[i].process(context, request, response, community);
    		}
    	}
    	catch (Exception e)
    	{
    		log.error("caught exception: ", e);
    		throw new ServletException(e);
    	}
    }
    
    
    /**
     * Show a collection home page, or deal with button press on home page
     * 
     * @param context
     *            Context object
     * @param request
     *            the HTTP request
     * @param response
     *            the HTTP response
     * @param community
     *            the community
     * @param collection
     *            the collection
     */
    private void collectionHome(Context context, HttpServletRequest request,
            HttpServletResponse response, Community community,
            Collection collection) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        // Handle click on a browse or search button
        if (!handleButton(request, response, collection.getHandle()))
        {
            // Will need to know whether to commit to DB
            boolean updated = false;

            // No search or browse button pressed, check for
            if (request.getParameter("submit_subscribe") != null)
            {
                // Subscribe button pressed.
                // Only registered can subscribe, so redirect unless logged in.
                if (context.getCurrentUser() == null &&
                    !Authenticate
                            .startAuthentication(context, request, response))

                    return;
                else
                { 
                    Subscribe.subscribe(context, context.getCurrentUser(),
                            collection);
                    updated = true;
                }
            }
            else if (request.getParameter("submit_unsubscribe") != null)
            {
                Subscribe.unsubscribe(context, context.getCurrentUser(),
                        collection);
                updated = true;
            }

            // display collection home page
            log.info(LogManager.getHeader(context, "view_collection",
                    "collection_id=" + collection.getID()));

            // perform any necessary pre-processing
            preProcessCollectionHome(context, request, response, collection);
            
            // Is the user logged in/subscribed?
            EPerson e = context.getCurrentUser();
            boolean subscribed = false;

            if (e != null)
            {
                subscribed = Subscribe.isSubscribed(context, e, collection);

                // is the user a COLLECTION_EDITOR?
                if (collection.canEditBoolean())
                {
                    // set a variable to create an edit button
                    request.setAttribute("editor_button", new Boolean(true));
                }

                // can they admin this collection?
                if (AuthorizeManager.authorizeActionBoolean(context,
                        collection, Constants.COLLECTION_ADMIN))
                {
                    request.setAttribute("admin_button", new Boolean(true));

                    // give them a button to manage submitter list
                    // what group is the submitter?
                    Group group = collection.getSubmitters();

                    if (group != null)
                    {
                        request.setAttribute("submitters", group);
                    }
                }

                // can they submit to this collection?
                if (AuthorizeManager.authorizeActionBoolean(context,
                        collection, Constants.ADD))
                {
                    request
                            .setAttribute("can_submit_button",
                                    new Boolean(true));

                }
                else
                {
                    request.setAttribute("can_submit_button",
                            new Boolean(false));
                }
            }

            // Forward to collection home page
            request.setAttribute("collection", collection);
            request.setAttribute("community", community);
            request.setAttribute("logged.in", new Boolean(e != null));
            request.setAttribute("subscribed", new Boolean(subscribed));
            JSPManager.showJSP(request, response, "/collection-home.jsp");

            if (updated)
            {
                context.complete();
            }
        }
    }

    private void preProcessCollectionHome(Context context, HttpServletRequest request,
            HttpServletResponse response, Collection collection)
    	throws ServletException, IOException, SQLException
    {
    	try
    	{
    		CollectionHomeProcessor[] chp = (CollectionHomeProcessor[]) PluginManager.getPluginSequence(CollectionHomeProcessor.class);
    		for (int i = 0; i < chp.length; i++)
    		{
    			chp[i].process(context, request, response, collection);
    		}
    	}
    	catch (Exception e)
    	{
    		log.error("caught exception: ", e);
    		throw new ServletException(e);
    	}
    }
    
    /**
     * Check to see if a browse or search button has been pressed on a community
     * or collection home page. If so, redirect to the appropriate URL.
     * 
     * @param request
     *            HTTP request
     * @param response
     *            HTTP response
     * @param handle
     *            Handle of the community/collection home page
     * 
     * @return true if a browse/search button was pressed and the user was
     *         redirected
     */
    private boolean handleButton(HttpServletRequest request,
            HttpServletResponse response, String handle) throws IOException
    {
        String button = UIUtil.getSubmitButton(request, "");
        String location = request.getParameter("location");
        String prefix = "/";
        String url = null;

        if (location == null)
        {
            return false;
        }

        /*
         * Work out the "prefix" to which to redirect If "/", scope is all of
         * DSpace, so prefix is "/" If prefix is a handle, scope is a community
         * or collection, so "/handle/1721.x/xxxx/" is the prefix.
         */
        if (!location.equals("/"))
        {
            prefix = "/handle/" + location + "/";
        }

        if (button.equals("submit_search")
                || (request.getParameter("query") != null))
        {
            /*
             * Have to check for search button and query - in some browsers,
             * typing a query into the box and hitting return doesn't produce a
             * submit button parameter. Redirect to appropriate search page
             */
            url = request.getContextPath()
                    + prefix
                    + "simple-search?query="
                    + URLEncoder.encode(request.getParameter("query"),
                            Constants.DEFAULT_ENCODING);
        }

        // If a button was pressed, redirect to appropriate page
        if (url != null)
        {
            response.sendRedirect(response.encodeRedirectURL(url));

            return true;
        }

        return false;
    }

    /**
     * Utility method to obtain the titles for the Items in the given list.
     * 
     * @param List
     *            of Items
     * @return array of corresponding titles
     */
    private String[] getItemTitles(List items)
    {
        String[] titles = new String[items.size()];

        for (int i = 0; i < items.size(); i++)
        {
            Item item = (Item) items.get(i);

            // FIXME: Should probably check for preferred language?
            DCValue[] titlesForThis = item.getDC("title", null, Item.ANY);

            // Just use the first title, if any
            if (titlesForThis.length == 0)
            {
                // No title at all!
                titles[i] = null;
            }
            else
            {
                // Use first title
                titles[i] = titlesForThis[0].value;
            }
        }

        return titles;
    }

    /**
     * Utility method obtain URLs for the most recent items
     * 
     * @param context
     *            DSpace context
     * @param items
     *            the items to get URLs for
     * @return an array of URLs (in Strings) corresponding to those items
     */
    private String[] getItemURLs(Context context, List items)
            throws SQLException
    {
        String[] urls = new String[items.size()];

        for (int i = 0; i < items.size(); i++)
        {
            Item item = (Item) items.get(i);
            urls[i] = "/handle/" + item.getHandle();
        }

        return urls;
    }

    /**
     * Utility method to produce a list of parent communities for a given
     * community, ending with the passed community, if include is true. If
     * commmunity is top-level, the array will be empty, or contain only the
     * passed community, if include is true. The array is ordered highest level
     * to lowest
     */
    private Community[] getParents(Community c, boolean include)
            throws SQLException
    {
        // Find all the "parent" communities for the community
        Community[] parents = c.getAllParents();

        // put into an array in reverse order
        int revLength = include ? (parents.length + 1) : parents.length;
        Community[] reversedParents = new Community[revLength];
        int index = parents.length - 1;

        for (int i = 0; i < parents.length; i++)
        {
            reversedParents[i] = parents[index - i];
        }

        if (include)
        {
            reversedParents[revLength - 1] = c;
        }

        return reversedParents;
    }
}
