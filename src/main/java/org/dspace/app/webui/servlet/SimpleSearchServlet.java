/*
 * SimpleSearchServlet.java
 *
 * Version: $Revision: 2553 $
 *
 * Date: $Date: 2008-01-16 08:46:44 -0800 (Wed, 16 Jan 2008) $
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

import java.io.IOException;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.app.webui.util.UIUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.LogManager;
import org.dspace.handle.HandleManager;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;
import org.dspace.sort.SortOption;

import org.dspace.app.util.EscapeUnescape;

/**
 * Servlet for handling a simple search.
 * <p>
 * All metadata is search for the value contained in the "query" parameter. If
 * the "location" parameter is present, the user's location is switched to that
 * location using a redirect. Otherwise, the user's current location is used to
 * constrain the query; i.e., if the user is "in" a collection, only results
 * from the collection will be returned.
 * <p>
 * The value of the "location" parameter should be ALL (which means no
 * location), a the ID of a community (e.g. "123"), or a community ID, then a
 * slash, then a collection ID, e.g. "123/456".
 * 
 * @author Robert Tansley
 * @version $Id: SimpleSearchServlet.java,v 1.17 2004/12/15 15:21:10 jimdowning
 *          Exp $
 */
public class SimpleSearchServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(SimpleSearchServlet.class);

    protected void doDSGet(Context context, HttpServletRequest request,
            HttpServletResponse response) throws ServletException, IOException,
            SQLException, AuthorizeException
    {
        // Get the query
        String query = request.getParameter("query");

        String encoded_query = query;
        int start = UIUtil.getIntParameter(request, "start");
        String advanced = request.getParameter("advanced");
        String fromAdvanced = request.getParameter("from_advanced");
        int sortBy = UIUtil.getIntParameter(request, "sort_by");//相關度 標題 遞交日期 更新日期
        String order = request.getParameter("order"); //遞增 遞減
        int rpp = UIUtil.getIntParameter(request, "rpp"); //每頁顯示筆數
        String advancedQuery = "";
        HashMap queryHash = new HashMap();

        // can't start earlier than 0 in the results!
        if (start < 0)
        {
            start = 0;
        }

        int collCount = 0;
        int commCount = 0;
        int itemCount = 0;

        Item[] resultsItems;
        Collection[] resultsCollections;
        Community[] resultsCommunities;

        QueryResults qResults = null;
        QueryArgs qArgs = new QueryArgs();
        SortOption sortOption = null;

        if (request.getParameter("etal") != null)
            qArgs.setEtAl(UIUtil.getIntParameter(request, "etal")); //每筆作者人數

        try
        {
            if (sortBy > 0)
            {
                sortOption = SortOption.getSortOption(sortBy);
                qArgs.setSortOption(sortOption);
            }

            if (SortOption.ASCENDING.equalsIgnoreCase(order))
            {
                qArgs.setSortOrder(SortOption.ASCENDING);
            }
            else
            {
                qArgs.setSortOrder(SortOption.DESCENDING);
            }
        }
        catch (Exception e)
        {
        }

        if (rpp > 0)
        {
            qArgs.setPageSize(rpp);
        }
        
        // if the "advanced" flag is set, build the query string from the
        // multiple query fields
        if (advanced != null)
        {
            query = qArgs.buildQuery(request);
            encoded_query = qArgs.buildQuery(request, true);
            
            log.info(LogManager.getHeader(context, "search", "advenced!=null: query=" + query + "||encode="+encoded_query));
            
            advancedQuery = qArgs.buildHTTPQuery(request);
        }

        // Ensure the query is non-null
        if (query == null)
        {
            query = "";
            encoded_query = "";
        }
        
        if (advanced == null
        	&& query.equals(encoded_query) == true)	//?��?��??����?�對���?���亦楊蝣�??��??
        {
        	
            //String encoded = EscapeUnescape.escape(query);
            String encoded = "";
            for (int i = 0; i < query.length(); i++)
            {
        	String c = query.substring(i, i+1);
                if (c.equals("(")
                    || c.equals(")")
                    || c.equals(" ")
                    || c.equals(":")
                    || c.equals("*")
                    || c.equals("%")
                    || c.equals("?"))
        		encoded = encoded + c;
        	else
        	{
                    try
                    {
        		encoded = encoded + EscapeUnescape.escape(c);
                    }
                    catch (Exception e) { }
        	}
            }
            if (encoded.equals(query) == false)
        	encoded_query = "(("+query+") OR ("+encoded+"))";
        
        log.info(LogManager.getHeader(context, "search", "simple do encoded: query=" + query + "||encode="+encoded_query));
        }
        
        log.info(LogManager.getHeader(context, "search", "simple query encoded: query=" + query + "||encode="+encoded_query));

        // Get the location parameter, if any
        String location = request.getParameter("location");//搜尋範圍
        String newURL;

        // If there is a location parameter, we should redirect to
        // do the search with the correct location.
        if ((location != null) && !location.equals(""))
        {
            String url = "";

            if (!location.equals("/"))
            {
                // Location is a Handle
                url = "/handle/" + location;
            }

            // Encode the query
            query = URLEncoder.encode(query, Constants.DEFAULT_ENCODING);
            encoded_query = URLEncoder.encode(encoded_query, Constants.DEFAULT_ENCODING);

            if (advancedQuery.length() > 0)
            {
                query = query + "&from_advanced=true&" + advancedQuery;
                encoded_query = encoded_query + "&from_advanced=true&" + advancedQuery;
            }
            
            log.info(LogManager.getHeader(context, "search", "redirect: query=" + query + "||encode="+encoded_query));

            // Do the redirect
            response.sendRedirect(response.encodeRedirectURL(request.getContextPath()+ url + "/simple-search?query=" + query));

            return;
        }

        // Build log information
        String logInfo = "";

        // Get our location
        Community community = UIUtil.getCommunityLocation(request);
        Collection collection = UIUtil.getCollectionLocation(request);

        // get the start of the query results page
        //        List resultObjects = null;
        //qArgs.setQuery(query);
        log.info(LogManager.getHeader(context, "search", "before setQuery: query=" + query + "||encode="+encoded_query));
        
        qArgs.setQuery(encoded_query);
        //qArgs.setQuery("(((蟡��??) AND (title:aaa01)) OR ((%u795e%u8599) AND (title:aaa01)))");
        qArgs.setStart(start);

        // Perform the search
        if (collection != null)
        {
            logInfo = "collection_id=" + collection.getID() + ",";

            // Values for drop-down box
            request.setAttribute("community", community);
            request.setAttribute("collection", collection);

            qResults = DSQuery.doQuery(context, qArgs, collection);
        }
        else if (community != null)
        {
            logInfo = "community_id=" + community.getID() + ",";

            request.setAttribute("community", community);

            // Get the collections within the community for the dropdown box
            request.setAttribute("collection.array", community.getCollections());

            qResults = DSQuery.doQuery(context, qArgs, community);
            //log.error(qResults.getHitCount());
        }
        else
        {
            // Get all communities for dropdown box
            Community[] communities = Community.findAll(context);
            request.setAttribute("community.array", communities);

            qResults = DSQuery.doQuery(context, qArgs);
        }

        // now instantiate the results and put them in their buckets
        for (int i = 0; i < qResults.getHitTypes().size(); i++)
        {
            Integer myType = (Integer) qResults.getHitTypes().get(i);

            // add the handle to the appropriate lists
            switch (myType.intValue())
            {
            case Constants.ITEM:
                itemCount++;
                break;

            case Constants.COLLECTION:
                collCount++;
                break;

            case Constants.COMMUNITY:
                commCount++;
                break;
            }
        }

        // Make objects from the handles - make arrays, fill them out
        resultsCommunities = new Community[commCount];
        resultsCollections = new Collection[collCount];
        resultsItems = new Item[itemCount];

        collCount = 0;
        commCount = 0;
        itemCount = 0;

        for (int i = 0; i < qResults.getHitTypes().size(); i++)
        {
            Integer myId    = (Integer) qResults.getHitIds().get(i);
            String myHandle = (String) qResults.getHitHandles().get(i);
            Integer myType  = (Integer) qResults.getHitTypes().get(i);

            // add the handle to the appropriate lists
            switch (myType.intValue())
            {
                case Constants.ITEM:
                if (myId != null)
                {
                    resultsItems[itemCount] = Item.find(context, myId);
                }
                else
                {
                    resultsItems[itemCount] = (Item)HandleManager.resolveToObject(context, myHandle);
                }

                if (resultsItems[itemCount] == null)
                {
                    throw new SQLException("Query \"" + query + "\" returned unresolvable item");
                }
                itemCount++;
                break;

                case Constants.COLLECTION:
                if (myId != null)
                {
                    resultsCollections[collCount] = Collection.find(context, myId);
                }
                else
                {
                    resultsCollections[collCount] = (Collection)HandleManager.resolveToObject(context, myHandle);
                }

                if (resultsCollections[collCount] == null)
                {
                    throw new SQLException("Query \"" + query + "\" returned unresolvable collection");
                }

                collCount++;
                break;

                case Constants.COMMUNITY:
                if (myId != null)
                {
                    resultsCommunities[commCount] = Community.find(context, myId);
                }
                else
                {
                    resultsCommunities[commCount] = (Community)HandleManager.resolveToObject(context, myHandle);
                }

                if (resultsCommunities[commCount] == null)
                {
                    throw new SQLException("Query \"" + query+ "\" returned unresolvable community");
                }

                commCount++;
                break;
            }
        }

        // Log
        log.info(LogManager.getHeader(context, "search", logInfo + "query=\""
                + query + "\",results=(" + resultsCommunities.length + ","
                + resultsCollections.length + "," + resultsItems.length + ")"));

        // Pass in some page qualities
        // total number of pages
        int pageTotal = 1 + ((qResults.getHitCount() - 1) / qResults.getPageSize());

        // current page being displayed
        int pageCurrent = 1 + (qResults.getStart() / qResults.getPageSize());

        // pageLast = min(pageCurrent+9,pageTotal)
        int pageLast = ((pageCurrent + 9) > pageTotal) ? pageTotal: (pageCurrent + 9);

        // pageFirst = max(1,pageCurrent-9)
        int pageFirst = ((pageCurrent - 9) > 1) ? (pageCurrent - 9) : 1;

        // Pass the results to the display JSP
        request.setAttribute("items", resultsItems);
        request.setAttribute("communities", resultsCommunities);
        request.setAttribute("collections", resultsCollections);

        request.setAttribute("pagetotal", new Integer(pageTotal));
        request.setAttribute("pagecurrent", new Integer(pageCurrent));
        request.setAttribute("pagelast", new Integer(pageLast));
        request.setAttribute("pagefirst", new Integer(pageFirst));

        request.setAttribute("queryresults", qResults);

        // And the original query string
        request.setAttribute("query", query);

        request.setAttribute("order",  qArgs.getSortOrder());
        request.setAttribute("sortedBy", sortOption);
        
        if ((fromAdvanced != null) && (qResults.getHitCount() == 0))
        {
            // send back to advanced form if no results
            Community[] communities = Community.findAll(context);
            request.setAttribute("communities", communities);
            request.setAttribute("no_results", "yes");

            queryHash = qArgs.buildQueryHash(request);

            Iterator i = queryHash.keySet().iterator();

            while (i.hasNext())
            {
                String key = (String) i.next();
                String value = (String) queryHash.get(key);

                request.setAttribute(key, value);
            }
            
            String[] searchIndexes = new String[0];
        
	    String indexesString = ConfigurationManager.getProperty("search.advanced.field.options","ANY, title, keywords, creator, dynasty, publisher, fulltext, preface");
	        
	if (indexesString != null)
	{
	    String[] indexes = indexesString.split(",");
	    ArrayList<String> list = new ArrayList<String>();
	    for (int j = 0; j < indexes.length; j++)
	    {
	        String index = indexes[j].trim();
	        if (index.equals("") == false)
                    list.add(index);
	    }
	    searchIndexes = new String[list.size()];
	    for (int j = 0; j < searchIndexes.length; j++)
	    {
	        searchIndexes[j] = list.get(j);
	    }
	}
	request.setAttribute("searchIndexes", searchIndexes);

        JSPManager.showJSP(request, response, "/search/advanced.jsp");
        }
        else
        {
            JSPManager.showJSP(request, response, "/search/results.jsp");
        }
    }
}
