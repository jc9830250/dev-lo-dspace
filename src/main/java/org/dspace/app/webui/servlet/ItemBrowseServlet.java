package org.dspace.app.webui.servlet;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.*;

import java.sql.SQLException;
import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;

public class ItemBrowseServlet extends DSpaceServlet
{

    @Override
    protected void doDSGet(Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        
		String s = request.getParameter("year");
		String s1 = request.getParameter("month");
		String s2 = request.getParameter("day");
                for (int a = s.length();--a>=0;){
                    if (!Character.isDigit(s.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "year is error");
                       
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;
                    }
                }

                for (int a = s1.length();--a>=0;){
                    if (!Character.isDigit(s1.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "month is error");
                        
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;

                    }
                }

                for (int a = s2.length();--a>=0;){
                    if (!Character.isDigit(s2.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "day is error");
                        
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;
                    }
                }

		int i = (request.getParameter("year") != null ? Integer.parseInt(s) : 0);
		int j = (request.getParameter("month") != null ? Integer.parseInt(s1) : 0);
		int k = (request.getParameter("day") != null ? Integer.parseInt(s2) : 0);

                if(i < 0 || j > 12 || j < 0 || k < 0 || k > 31)
                {
                    request.setAttribute("errorMessage", "date is error");
                    
                    JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                    return;
                }
		request.setAttribute("dayindex", Integer.valueOf(k));
		request.setAttribute("monthindex", Integer.valueOf(j));
		request.setAttribute("yearindex", Integer.valueOf(i));
        
        JSPManager.showJSP(request, response, "/itembrowse.jsp");
    }
	
    @Override
	protected void doDSPost(Context context,
        HttpServletRequest request,
        HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
        
		String s = request.getParameter("year");
		String s1 = request.getParameter("month");
		String s2 = request.getParameter("day");
                for (int a = s.length();--a>=0;){
                    if (!Character.isDigit(s.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "year is error");
                        
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;
                    }
                }

                for (int a = s1.length();--a>=0;){
                    if (!Character.isDigit(s1.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "month is error");
                       
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;
                    }
                }

                for (int a = s2.length();--a>=0;){
                    if (!Character.isDigit(s2.charAt(a)))
                    {
                        request.setAttribute("errorMessage", "day is error");
                        
                        JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                        return;
                    }
                }

		int i = (request.getParameter("year") != null ? Integer.parseInt(s) : 0);
		int j = (request.getParameter("month") != null ? Integer.parseInt(s1) : 0);
		int k = (request.getParameter("day") != null ? Integer.parseInt(s2) : 0);

                if(i < 0 || j > 12 || j < 0 || k < 0 || k > 31)
                {
                    request.setAttribute("errorMessage", "date is error");
                    
                    JSPManager.showJSP(request, response, "/error/itembrowserror.jsp");
                    return;
                }
		request.setAttribute("dayindex", Integer.valueOf(k));
		request.setAttribute("monthindex", Integer.valueOf(j));
		request.setAttribute("yearindex", Integer.valueOf(i));

        JSPManager.showJSP(request, response, "/itembrowse.jsp");
    }
}
		
	

		