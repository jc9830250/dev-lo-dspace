/*
 * UIUtil.java
 *
 * Version: $Revision: 2168 $
 *
 * Date: $Date: 2007-08-27 17:40:09 -0500 (Mon, 27 Aug 2007) $
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
package org.dspace.app.webui.util;


import java.io.StringWriter;
import java.sql.SQLException;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.jstl.core.Config;

import org.apache.log4j.Logger;
import org.dspace.app.util.Util;
import org.dspace.authenticate.AuthenticationManager;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.DCDate;
import org.dspace.content.DSpaceObject;
import org.dspace.eperson.Group;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Email;
import org.dspace.core.I18nUtil;
import org.dspace.eperson.EPerson;
import org.dspace.authorize.AuthorizeException;

import javax.servlet.jsp.PageContext;
import java.io.PrintWriter;
import javax.servlet.jsp.jstl.fmt.LocaleSupport;


/**
 * Miscellaneous UI utility methods
 * 
 * @author Robert Tansley
 * @version $Revision: 2168 $
 */
public class UIUtil extends Util
{
    /** log4j category */
    public static Logger log = Logger.getLogger(UIUtil.class);

    /**
     * Obtain a new context object. If a context object has already been created
     * for this HTTP request, it is re-used, otherwise it is created. If a user
     * has authenticated with the system, the current user of the context is set
     * appropriately.
     * 
     * @param request
     *            the HTTP request
     * 
     * @return a context object
     */
    public static Context obtainContext(HttpServletRequest request)
            throws SQLException
    {
        
        //Set encoding to UTF-8, if not set yet
        //This avoids problems of using the HttpServletRequest
        //in the getSpecialGroups() for an AuthenticationMethod,  
        //which causes the HttpServletRequest to default to 
        //non-UTF-8 encoding.
        try
        {
            if(request.getCharacterEncoding()==null)
                request.setCharacterEncoding(Constants.DEFAULT_ENCODING);
        }
        catch(Exception e)
        {
            log.error("Unable to set encoding to UTF-8.", e);
        }
        
        Context c = (Context) request.getAttribute("dspace.context");
        

        if (c == null)
        {
            // No context for this request yet
            c = new Context();
            HttpSession session = request.getSession();

            // See if a user has authentication
            Integer userID = (Integer) session.getAttribute(
                    "dspace.current.user.id");

            if (userID != null)
            {
                String remAddr = (String)session.getAttribute("dspace.current.remote.addr");
                if (remAddr != null && remAddr.equals(request.getRemoteAddr()))
                {
                EPerson e = EPerson.find(c, userID.intValue());

                Authenticate.loggedIn(c, request, e);
            }
                else
                {
                    log.warn("POSSIBLE HIJACKED SESSION: request from "+
                             request.getRemoteAddr()+" does not match original "+
                             "session address: "+remAddr+". Authentication rejected.");
                }
            }

            // Set any special groups - invoke the authentication mgr.
            int[] groupIDs = AuthenticationManager.getSpecialGroups(c, request);

            for (int i = 0; i < groupIDs.length; i++)
            {
                c.setSpecialGroup(groupIDs[i]);
                log.debug("Adding Special Group id="+String.valueOf(groupIDs[i]));
            }

            // Set the session ID and IP address
            c.setExtraLogInfo("session_id=" + request.getSession().getId() + ":ip_addr=" + request.getRemoteAddr());

            // Store the context in the request
            request.setAttribute("dspace.context", c);
        }
        
        // Set the locale to be used
        Locale sessionLocale = getSessionLocale(request);
        Config.set(request.getSession(), Config.FMT_LOCALE, sessionLocale);
        c.setCurrentLocale(sessionLocale);

        return c;
    }

    /**
     * Get the current community location, that is, where the user "is". This
     * returns <code>null</code> if there is no location, i.e. "all of DSpace"
     * is the location.
     * 
     * @param request
     *            current HTTP request
     * 
     * @return the current community location, or null
     */
    public static Community getCommunityLocation(HttpServletRequest request)
    {
        return ((Community) request.getAttribute("dspace.community"));
    }

    /**
     * Get the current collection location, that is, where the user "is". This
     * returns null if there is no collection location, i.e. the location is
     * "all of DSpace" or a community.
     * 
     * @param request
     *            current HTTP request
     * 
     * @return the current collection location, or null
     */
    public static Collection getCollectionLocation(HttpServletRequest request)
    {
        return ((Collection) request.getAttribute("dspace.collection"));
    }

    /**
     * Put the original request URL into the request object as an attribute for
     * later use. This is necessary because forwarding a request removes this
     * information. The attribute is only written if it hasn't been before; thus
     * it can be called after a forward safely.
     * 
     * @param request
     *            the HTTP request
     */
    public static void storeOriginalURL(HttpServletRequest request)
    {
        String orig = (String) request.getAttribute("dspace.original.url");

        if (orig == null)
        {
            String fullURL = request.getRequestURL().toString();

            if (request.getQueryString() != null)
            {
                fullURL = fullURL + "?" + request.getQueryString();
            }

            request.setAttribute("dspace.original.url", fullURL);
        }
    }

    /**
     * Get the original request URL.
     * 
     * @param request
     *            the HTTP request
     * 
     * @return the original request URL
     */
    public static String getOriginalURL(HttpServletRequest request)
    {
        // Make sure there's a URL in the attribute
        storeOriginalURL(request);

        return ((String) request.getAttribute("dspace.original.url"));
    }

    /**
     * Write a human-readable version of a DCDate.
     * 
     * @param d
     *            the date
     * @param time
     *            if true, display the time with the date
     * @param localTime
     *            if true, adjust for local timezone, otherwise GMT
     * @param request
     *            the servlet request           
     * 
     * @return the date in a human-readable form.
     */
    public static String displayDate(DCDate d, boolean time, boolean localTime, HttpServletRequest request)
    {
        StringBuffer sb = new StringBuffer();
        Locale locale = ((Context)request.getAttribute("dspace.context")).getCurrentLocale();
        if (locale == null) locale = I18nUtil.DEFAULTLOCALE;

        if (d != null)
        {
            int year;
            int month;
            int day;
            int hour;
            int minute;
            int second;

            if (localTime)
            {
                year = d.getYear();
                month = d.getMonth();
                day = d.getDay();
                hour = d.getHour();
                minute = d.getMinute();
                second = d.getSecond();
            }
            else
            {
                year = d.getYearGMT();
                month = d.getMonthGMT();
                day = d.getDayGMT();
                hour = d.getHourGMT();
                minute = d.getMinuteGMT();
                second = d.getSecondGMT();
            }

            if (year > -1)
            {
                if (month > -1)
                {
                    if (day > -1)
                    {
                        sb.append(day + "-");
                    }
                    String monthName = DCDate.getMonthName(month, getSessionLocale(request));
                    int monthLength = monthName.length();
                    monthLength = monthLength > 2 ? 3 : monthLength;
                    sb.append(monthName.substring(0, monthLength) + "-");
                }

                sb.append(year + " ");
            }

            if (time && (hour > -1))
            {
                String hr = String.valueOf(hour);

                while (hr.length() < 2)
                {
                    hr = "0" + hr;
                }

                String mn = String.valueOf(minute);

                while (mn.length() < 2)
                {
                    mn = "0" + mn;
                }

                String sc = String.valueOf(second);

                while (sc.length() < 2)
                {
                    sc = "0" + sc;
                }

                sb.append(hr + ":" + mn + ":" + sc + " ");
            }
        }
        else
        {
            sb.append("Unset");
        }

        return (sb.toString());
    }

    /**
     * Return a string for logging, containing useful information about the
     * current request - the URL, the method and parameters.
     * 
     * @param request
     *            the request object.
     * @return a multi-line string containing information about the request.
     */
    public static String getRequestLogInfo(HttpServletRequest request)
    {
        String report;

        report = "-- URL Was: " + getOriginalURL(request) + "\n";
        report = report + "-- Method: " + request.getMethod() + "\n";

        // First write the parameters we had
        report = report + "-- Parameters were:\n";

        Enumeration e = request.getParameterNames();

        while (e.hasMoreElements())
        {
            String name = (String) e.nextElement();

            if (name.equals("login_password"))
            {
                // We don't want to write a clear text password
                // to the log, even if it's wrong!
                report = report + "-- " + name + ": *not logged*\n";
            }
            else
            {
                report = report + "-- " + name + ": \""
                        + request.getParameter(name) + "\"\n";
            }
        }

        return report;
    }
    
    
    /**
     * Get the Locale for a session according to the user's language selection or language preferences.
     * Order of selection
     * - language selected via UI
     * - language as set by application
     * - language browser default
     * 
     * @param request
     *        the request Object
     * @return supportedLocale
     *         Locale supported by this DSpace Instance for this request
     */
    public static Locale getSessionLocale(HttpServletRequest request)

    {
        String paramLocale = request.getParameter("locale");
        Locale sessionLocale = null;
        Locale supportedLocale = null;

        if (paramLocale != null && paramLocale != "")
        {
            /* get session locale according to user selection */
            sessionLocale = new Locale(paramLocale);
        }
        
     
        if (sessionLocale == null)
        {
            /* get session locale set by application */
            HttpSession session = request.getSession();
            sessionLocale = (Locale) Config.get(session, Config.FMT_LOCALE);
        }

        /*
         * if session not set by selection or application then default browser
         * locale
         */
        if (sessionLocale == null)
        {
            sessionLocale = request.getLocale();
        }
        
        if (sessionLocale == null)
        {
            sessionLocale = I18nUtil.DEFAULTLOCALE;
        }
        supportedLocale =  I18nUtil.getSupportedLocale(sessionLocale);
        
        return supportedLocale;
    }    

    
    /**
     * Send an alert to the designated "alert recipient" - that is, when a
     * database error or internal error occurs, this person is sent an e-mail
     * with details.
     * <P>
     * The recipient is configured via the "alert.recipient" property in
     * <code>dspace.cfg</code>. If this property is omitted, no alerts are
     * sent.
     * <P>
     * This method "swallows" any exception that might occur - it will just be
     * logged. This is because this method will usually be invoked as part of an
     * error handling routine anyway.
     * 
     * @param request
     *            the HTTP request leading to the error
     * @param exception
     *            the exception causing the error, or null
     */
    public static void sendAlert(HttpServletRequest request, Exception exception)
    {
        String logInfo = UIUtil.getRequestLogInfo(request);
        Context c = (Context) request.getAttribute("dspace.context");

        try
        {
            String recipient = ConfigurationManager
                    .getProperty("alert.recipient");

            if (recipient != null)
            {
                Email email = ConfigurationManager.getEmail(I18nUtil.getEmailFilename(c.getCurrentLocale(), "internal_error"));

                email.addRecipient(recipient);
                email.addArgument(ConfigurationManager
                        .getProperty("dspace.url"));
                email.addArgument(new Date());
                email.addArgument(request.getSession().getId());
                email.addArgument(logInfo);

                String stackTrace;

                if (exception != null)
                {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    exception.printStackTrace(pw);
                    pw.flush();
                    stackTrace = sw.toString();
                }
                else
                {
                    stackTrace = "No exception";
                }

                email.addArgument(stackTrace);
                email.send();
            }
        }
        catch (Exception e)
        {
            // Not much we can do here!
            log.warn("Unable to send email alert", e);
        }
    }
    
    public static String substring(String input, int start, int end)
    {
    	if (input.length() < end || end < start)
    		end = input.length();
    	return input.substring(start, end);
    }
    
    public static String substring(int inputInt, int start, int end)
    {
    	String input = "" + inputInt;
    	if (input.length() < end || end < start)
    		end = input.length();
    	return input.substring(start, end);
    }
    
    public static String substring(String input, int start)
    {
    	return substring(input, start, input.length());
    }
    
    public static String substring(int inputInt, int start)
    {
    	String input = "" + inputInt;
    	return substring(input, start, input.length());
    }
    
    public static String showException(Exception e)
    {
    	return showException(e, false);
    }
    
    public static String showException(Exception e, boolean inComment)
    { 
		String output = "";
		
		output = "<div style=\"border: 3px double black; background-color:white;\">" + "\n"
			+ "Message: <br /><pre>\n" + e.getMessage() + "\n</pre>\n"
			+ "<br /><hr /><br /><pre>" + "\n"
			+ e.getStackTrace() + "\n"
			+ "</pre></div>\n";
		
		if (inComment)
		{
			output = "<!-- \n"+output+"\n -->\n";
		}
		
		return output;
    }
    
    public static int getCurrentUserID(HttpServletRequest request)
    	throws SQLException
    {
    	Context c = obtainContext(request);
    	EPerson user = c.getCurrentUser();
    	if (user == null)
    		return -1;
    	else
    		return user.getID();
    }
    
    public static long getDateID()
    {
    	Date d = new Date();
    	
    	String y =  Integer.toString(d.getYear() + 1900);
    	
    	String m = Integer.toString(d.getMonth() + 1);
    	if (m.length() < 2)
    		m = "0" + m;
    	String day = Integer.toString(d.getDate());
    	if (day.length() < 2)
    		day = "0" + d;
    	
    	String h = Integer.toString(d.getHours());
    	if (h.length() < 2)
    		h = "0" + h;
    	String min = Integer.toString(d.getMinutes());
    	if (min.length() < 2)
    		min = "0" + min;
    	String s = Integer.toString(d.getSeconds());
    	if (s.length() < 2)
    		s = "0" + s;
    	
    	String date = y
    		+ m
    		+ day
    		+ h
    		+ min
    		+ s;
    	
    	long dateID = -1;
    	try
    	{
    		dateID = Long.parseLong(date);
    	}
    	catch (Exception e) { }
    	
    	return dateID;
    }
    
    public static String getAuthorizeActionLocalizeMessage(PageContext pageContext, String action) {
    	if (action.equals("OBSOLETE (DELETE)"))
            action = "DELETE";
        if (action.equals("..."))
        	return action;
        
        return LocaleSupport.getLocalizedMessage(pageContext, 
            "jsp.dspace-admin.general.action." + action);
    }
    
    public static String getGroupLocalizeName(Context context, PageContext pageContext, Group group)
    {
    	String name = group.getName();
    	return getGroupLocalizeName(context, pageContext, name);
    }
    public static String getGroupLocalizeName(Context context, PageContext pageContext, String name)
    {
    	if (name.equals("Administrator"))
    	{
    		name = LocaleSupport.getLocalizedMessage(pageContext, 
		            "jsp.dspace-admin.general.group.Administrator");
    	}
    	else if (name.equals("Anonymous"))
    	{
    		name = LocaleSupport.getLocalizedMessage(pageContext, 
		            "jsp.dspace-admin.general.group.Anonymous");
    	}
    	else if (name.startsWith("COLLECTION_"))
    	{
    		String[] specGroup = {"DEFAULT_ITEM_READ"
    			, "ADMIN"
    			, "SUBMIT"
    			, "WORKFLOW_STEP_1"
    			, "WORKFLOW_STEP_2"
    			, "WORKFLOW_STEP_3"
    			, "DEFAULT_BITSTREAM_READ"
    		};
    		
    		for (int i = 0; i < specGroup.length; i++)
    		{
    			if (name.endsWith("_" + specGroup[i]))
    			{
    				//取得合集的編號
    				int start = name.indexOf("_") + 1;
    				int end = name.indexOf("_", start);
    				if (start >= end)
    					break;
    				else
    				{
	    				String idString = name.substring(start, end);
	    				int id = -1;
	    				try
	    				{
	    					id = Integer.parseInt(idString);
	    					
	    					Collection collection = Collection.find(context, id);
	    					if (collection != null)
	    					{
	    						String[] option = {LocaleSupport.getLocalizedMessage(pageContext, "jsp.dspace-admin.general.group.collection")
	    							, collection.getName()
	    							, collection.getID() + ""
	    						};
		    					name = LocaleSupport.getLocalizedMessage(pageContext, 
		            				"jsp.dspace-admin.general.group." + specGroup[i], option);
	            			}
	            			break;
	    				}
	    				catch (Exception e) { }
	            	}	//if (start >= end) else
    			}	//if (name.endsWith("_" + specGroup[i]))
    		}	//for (int i = 0; i < specGroup.length; i++)
    		
    	}
    	return name;
    }
    
    public static boolean isSpecialGroup(Context context, Group group)
    {
    	return isSpecialGroup(context, group.getName());
    }
    
    public static boolean isSpecialGroup(Context context, String name)
    {
    	if (name.equals("Administrator")
    		|| name.equals("Anonymous"))
    	{
    		return true;
    	}
    	else if (name.startsWith("COLLECTION_"))
    	{
    		String[] specGroup = {"DEFAULT_ITEM_READ"
    			, "ADMIN"
    			, "SUBMIT"
    			, "WORKFLOW_STEP_1"
    			, "WORKFLOW_STEP_2"
    			, "WORKFLOW_STEP_3"
    			, "DEFAULT_BITSTREAM_READ"
    		};
    		for (int i = 0; i < specGroup.length; i++)
    		{
    			if (name.endsWith("_" + specGroup[i]))
    			{
    				//取得合集的編號
    				int start = name.indexOf("_") + 1;
    				int end = name.indexOf("_", start);
    				if (start >= end)
    					break;
    				else
    				{
	    				String idString = name.substring(start, end);
	    				int id = -1;
	    				try
	    				{
	    					id = Integer.parseInt(idString);
	    					
	    					Collection collection = Collection.find(context, id);
	    					if (collection != null)
	    					{
	    						return true;
	            			}
	            			break;
	    				}
	    				catch (Exception e) { }
	            	}
    			}
    		}
    	}
    	
    	return false;
    }
    
    public static void addCounter(HttpServletRequest request, DSpaceObject dso) throws SQLException,AuthorizeException
    {
    	if (ConfigurationManager.getBooleanProperty("counter.enable", true))
    	{
	    	HttpSession session = request.getSession();
	    	
	    	String id = dso.getType() + "-" +  dso.getID();
	    	
	    	if ( session.getAttribute(id) == null )
	    	{
	    		session.setAttribute(id, true);
	    		dso.addCount();
	    	}
	    }
    }
}
