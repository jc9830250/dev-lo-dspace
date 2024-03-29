/*
 * AbstractBrowserServlet.java
 *
 * Version: $Revision: 1189 $
 *
 * Date: $Date: 2005-04-20 15:23:44 +0100 (Wed, 20 Apr 2005) $
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
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.dspace.app.webui.util.UIUtil;
import org.dspace.authorize.AuthorizeException;
import org.dspace.browse.BrowseEngine;
import org.dspace.browse.BrowseException;
import org.dspace.browse.BrowseIndex;
import org.dspace.browse.BrowseInfo;
import org.dspace.browse.BrowserScope;
import org.dspace.sort.SortOption;
import org.dspace.sort.SortException;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DCValue;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;
import org.dspace.core.LogManager;
import java.net.URLDecoder;
import org.dspace.app.util.EscapeUnescape;

import java.util.ArrayList;
import java.util.List;

/**
 * Servlet for browsing through indices, as they are defined in
 * the configuration.  This class can take a wide variety of inputs from
 * the user interface:
 *
 * - type:  the type of browse (index name) being performed
 * - order: (ASC | DESC) the direction for result sorting
 * - value: A specific value to find items around.  For example the author name or subject
 * - month: integer specification of the month of a date browse
 * - year: integer specification of the year of a date browse
 * - starts_with: string value at which to start browsing
 * - vfocus: start browsing with a value of this string
 * - focus: integer id of the item at which to start browsing
 * - rpp: integer number of results per page to display
 * - sort_by: integer specification of the field to search on
 * - etal: integer number to limit multiple value items specified in config to
 *
 * @author Richard Jones
 * @version $Revision:  $
 */
public abstract class AbstractBrowserServlet extends DSpaceServlet
{
    /** log4j category */
    private static Logger log = Logger.getLogger(AbstractBrowserServlet.class);

    public AbstractBrowserServlet()
    {
        super();
    }

    /**
     * Create a BrowserScope from the current request
     *
     * @param context The database context
     * @param request The servlet request
     * @param response The servlet response
     * @return A BrowserScope for the current parameters
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    protected BrowserScope getBrowserScopeForRequest(Context context, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException, AuthorizeException
    {
        try
        {
            // first, lift all the stuff out of the request that we might need
            String type = request.getParameter("type");
            String order = request.getParameter("order");
            String value = request.getParameter("value");
            String valueLang = request.getParameter("value_lang");
            String month = request.getParameter("month");
            String year = request.getParameter("year");
            String startsWith = request.getParameter("starts_with");
            String valueFocus = request.getParameter("vfocus");
            String valueFocusLang = request.getParameter("vfocus_lang");
            int focus = UIUtil.getIntParameter(request, "focus");
            int offset = UIUtil.getIntParameter(request, "offset");
            int resultsperpage = UIUtil.getIntParameter(request, "rpp");
            int sortBy = UIUtil.getIntParameter(request, "sort_by");
            String sortType = request.getParameter("sort_type");
            int etAl = UIUtil.getIntParameter(request, "etal");
            
            String mode = request.getParameter("mode");
            if (mode == null) 
            	mode = "simple";
            
            // get the community or collection location for the browse request
            // Note that we are only interested in getting the "smallest" container,
            // so if we find a collection, we don't bother looking up the community
            Collection collection = null;
            Community community = null;
            collection = UIUtil.getCollectionLocation(request);
            if (collection == null)
            {
                community = UIUtil.getCommunityLocation(request);
            }

            // process the input, performing some inline validation
            BrowseIndex bi = null;
            if (type != null && !"".equals(type))
            {
                bi = BrowseIndex.getBrowseIndex(type);
            }

            if (bi == null)
            {
                if (sortBy > 0)
                    bi = BrowseIndex.getBrowseIndex(SortOption.getSortOption(sortBy));
                else
                    bi = BrowseIndex.getBrowseIndex(SortOption.getDefaultSortOption());
            }

            // If we don't have a sort column
            if (bi != null && sortBy == -1)
            {
                // Get the default one
                SortOption so = bi.getSortOption();
                if (so != null)
                {
                    sortBy = so.getNumber();
                }
            }
            else if (bi != null && bi.isItemIndex() && !bi.isInternalIndex())
            {
                // If a default sort option is specified by the index, but it isn't
                // the same as sort option requested, attempt to find an index that
                // is configured to use that sort by default
                // This is so that we can then highlight the correct option in the navigation
                SortOption bso = bi.getSortOption();
                SortOption so = SortOption.getSortOption(sortBy);
                if ( bso != null && bso != so)
                {
                    BrowseIndex newBi = BrowseIndex.getBrowseIndex(so);
                    if (newBi != null)
                    {
                        bi   = newBi;
                        type = bi.getName();
                    }
                }
            }

            if (order == null && bi != null)
            {
                order = bi.getDefaultOrder();
            }

            // If the offset is invalid, reset to 0
            if (offset < 0)
            {
                offset = 0;
            }

            // if no resultsperpage set, default to 20
            if (resultsperpage < 0)
            {
                resultsperpage = 20;
            }

            // if year and perhaps month have been selected, we translate these into "startsWith"
            // if startsWith has already been defined then it is overwritten
            if (year != null && !"".equals(year) && !"-1".equals(year))
            {
                startsWith = year;
                if ((month != null) && !"-1".equals(month) && !"".equals(month))
                {
                    // subtract 1 from the month, so the match works appropriately
                    if ("ASC".equals(order))
                    {
                        month = Integer.toString((Integer.parseInt(month) - 1));
                    }

                    // They've selected a month as well
                    if (month.length() == 1)
                    {
                        // Ensure double-digit month number
                        month = "0" + month;
                    }

                    startsWith = year + "-" + month;

                    if ("ASC".equals(order))
                    {
                        startsWith = startsWith + "-32";
                    }
                }
            }

            // determine which level of the browse we are at: 0 for top, 1 for second
            int level = 0;
            if (value != null)
            {
                level = 1;
            }

            // if sortBy is still not set, set it to 0, which is default to use the primary index value
            if (sortBy == -1)
            {
                sortBy = 0;
            }

            // figure out the setting for author list truncation
            if (etAl == -1)     // there is no limit, or the UI says to use the default
            {
                int limitLine = ConfigurationManager.getIntProperty("webui.browse.author-limit");
                if (limitLine != 0)
                {
                    etAl = limitLine;
                }
            }
            else  // if the user has set a limit
            {
                if (etAl == 0)  // 0 is the user setting for unlimited
                {
                    etAl = -1;  // but -1 is the application setting for unlimited
                }
            }

            // log the request
            String comHandle = "n/a";
            if (community != null)
            {
                comHandle = community.getHandle();
            }
            String colHandle = "n/a";
            if (collection != null)
            {
                colHandle = collection.getHandle();
            }

            String arguments = "type=" + type + ",order=" + order + ",value=" + value +
                ",month=" + month + ",year=" + year + ",starts_with=" + startsWith +
                ",vfocus=" + valueFocus + ",focus=" + focus + ",rpp=" + resultsperpage +
                ",sort_by=" + sortBy + ",community=" + comHandle + ",collection=" + colHandle +
                ",level=" + level + ",etal=" + etAl + ",sort_type=" + sortType+",";

            log.info(LogManager.getHeader(context, "browse", arguments));

            // set up a BrowseScope and start loading the values into it
            BrowserScope scope = new BrowserScope(context);
            scope.setBrowseIndex(bi);
            scope.setOrder(order);
            scope.setFilterValue(value);
            scope.setFilterValueLang(valueLang);
            scope.setJumpToItem(focus);
            scope.setJumpToValue(valueFocus);
            scope.setJumpToValueLang(valueFocusLang);
            scope.setStartsWith(startsWith);
            scope.setOffset(offset);
            scope.setResultsPerPage(resultsperpage);
            scope.setSortBy(sortBy);
            scope.setBrowseLevel(level);
            scope.setEtAl(etAl);
            scope.setMode(mode);
            scope.setSortType(sortType);

            // assign the scope of either Community or Collection if necessary
            if (community != null)
            {
                scope.setBrowseContainer(community);
            }
            else if (collection != null)
            {
                scope.setBrowseContainer(collection);
            }

            // For second level browses on metadata indexes, we need to adjust the default sorting
            if (bi != null && bi.isMetadataIndex() && scope.isSecondLevel() && scope.getSortBy() <= 0)
            {
                scope.setSortBy(1);
            }
            
            //??��?��?��?��?�filterDCValue??��?��?��?�網??中�?��?��?��?�fdc=dc.title=？�?�格�?
            ArrayList<DCValue> fdcList = new ArrayList<DCValue>();
            ArrayList<String> foList = new ArrayList<String>();
            int fdc_index = 0;
            String fdcParameter = request.getParameter("fdc["+fdc_index+"]");
            
            String p = "";
            
            while(fdcParameter != null)
            {
            	/*
            	try
            	{
            	*/
            		//log.info(LogManager.getHeader(context, "fdcParameter", "value="+fdcParameter));
            		
            		//?��?��類別
            		String operator = "=";
            		int opEqual = fdcParameter.indexOf("=");
            		int opMore = fdcParameter.indexOf(">");
            		int opLess = fdcParameter.indexOf("<");
            		
            		if (opMore != -1 
            			&& (opEqual == -1 || (opEqual != -1 && opMore < opEqual))
            			&& (opLess == -1 || (opLess != -1 && opMore < opLess)))
            			operator = ">";
            		else if (opLess != -1 
            			&& (opEqual == -1 || (opEqual != -1 && opLess < opEqual))
            			&& (opMore == -1 || (opMore != -1 && opLess < opMore)))
            			operator = "<";
            		
            		p = p + "!" + operator + "("+fdcParameter.indexOf(operator)+")["+fdcParameter+"]";
            		
            		if (fdcParameter.indexOf(operator) > -1)
            		{
	            		foList.add(operator);
	            		
	            		DCValue fdc = new DCValue();
	            		if (fdcParameter.length() > 2 && fdcParameter.substring(0,2).equals("*="))
	            		{
	            			fdc.schema = "*";
	            			fdc.element = "*";
	            			fdc.qualifier = "*";
	            		}
	            		else
	            		{
			            	String[] fdcField = fdcParameter.substring(0, fdcParameter.indexOf(operator))
			            		.trim()
			            		.split("\\.");
			            	fdc.schema = fdcField[0];
			            	fdc.element = fdcField[1];
			            	if (fdcField.length > 2)
			            		fdc.qualifier = fdcField[2];
		            	}
		            	fdc.value = fdcParameter.substring(fdcParameter.indexOf(operator) + 1, 
		            			fdcParameter.length()).trim();
		            	
		            	fdc.value = EscapeUnescape.unescape(fdc.value);
		            	
	            		p = p + "|" + fdcParameter;
		            	
		            	fdcList.add(fdc);
		            }
		        /*
	            }
	            catch (Exception e) 
	            {
	            	log.error("get filter DCValue error: ", e);
	            	log.info(LogManager.getHeader(context, "getFDC error: fdc["+fdc_index+"]", p ));
	            	//throws new ServletException("get filter DCValue error");
	            }
	            */
	            fdc_index++;
	            p = p + "->fdc["+fdc_index+"]";
	            fdcParameter = request.getParameter("fdc["+fdc_index+"]");
            }
			log.info(LogManager.getHeader(context, "getFDC: ", p ));
            
            if (fdcList.size() > 0)
            {
            	DCValue[] filterDCValue = new DCValue[fdcList.size()];
            	String[] filterOperator = new String[foList.size()];
            	for (int i = 0; i < fdcList.size(); i++)
            	{
            		filterDCValue[i] = (DCValue) fdcList.get(i);
            		filterOperator[i] = (String) foList.get(i);
            	}
		        scope.setFilterDCValue(filterDCValue);
		        scope.setFilterOperator(filterOperator);
            }
            
            ArrayList<String> connList = new ArrayList<String>();
            int conn_index = 0;
            String connParameter = request.getParameter("fc["+conn_index+"]");
            p = "";
            while(connParameter != null)
            {
	            if (connParameter != null)
	            {
	            	try
	            	{
	            		p = p + "|" + connParameter;
	            		connList.add(connParameter);
		            }
		            catch (Exception e) 
		            {
		            	log.error("get Connector error: ", e);
		            }
	            }
	            
	            conn_index++;
	            p = p + "->fc["+conn_index+"]";
	            connParameter = request.getParameter("fc["+conn_index+"]");
            }
            
            if (connList.size() > 0)
            {
            	String[] filterConn = new String[connList.size()];
            	for (int i = 0; i < connList.size(); i++)
            		filterConn[i] = connList.get(i);
		        scope.setFilterConnector(filterConn);
            }
            log.info(LogManager.getHeader(context, "getFC: ", p ));

            return scope;
        }
        catch (SortException se)
        {
            log.error("caught exception: ", se);
            throw new ServletException(se);
        }
        catch (BrowseException e)
        {
            log.error("caught exception: ", e);
            throw new ServletException(e);
        }
    }

    /**
     * Do the usual DSpace GET method.  You will notice that browse does not currently
     * respond to POST requests.
     */
    protected void processBrowse(Context context, BrowserScope scope, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException,
            AuthorizeException
    {
        try
        {
            BrowseIndex bi = scope.getBrowseIndex();

            // now start up a browse engine and get it to do the work for us
            BrowseEngine be = new BrowseEngine(context);
            BrowseInfo binfo = be.browse(scope);

            request.setAttribute("browse.info", binfo);

            if (binfo.hasResults())
            {
                if (bi.isMetadataIndex() && !scope.isSecondLevel())
                {
                    showSinglePage(context, request, response);
                }
                else
                {
                    showFullPage(context, request, response);
                }
            }
            else
            {
                showNoResultsPage(context, request, response);
            }
        }
        catch (BrowseException e)
        {
            log.error("caught exception: ", e);
            throw new ServletException(e);
        }
    }

    /**
     * Display the error page
     *
     * @param context
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    protected abstract void showError(Context context, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException,
            AuthorizeException;

    /**
     * Display the No Results page
     *
     * @param context
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    protected abstract void showNoResultsPage(Context context, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException,
            AuthorizeException;

    /**
     * Display the single page.  This is the page which lists just the single values of a
     * metadata browse, not individual items.  Single values are links through to all the items
     * that match that metadata value
     *
     * @param context
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     * @throws SQLException
     * @throws AuthorizeException
     */
    protected abstract void showSinglePage(Context context, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException,
            AuthorizeException;

    protected abstract void showFullPage(Context context, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException, SQLException,
            AuthorizeException;
}
