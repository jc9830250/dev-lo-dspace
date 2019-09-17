package org.dspace.app.webui.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Context;


public class MonthDecServlet extends DSpaceServlet 
{
	public void doDSPost(Context context, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
		String month1 = request.getParameter("decmonth");
		String year1 = request.getParameter("styear");
		int month2 = (request.getParameter("decmonth") != null ? Integer.parseInt(month1) : 0);
		int year2 = (request.getParameter("styear") != null ? Integer.parseInt(year1) : 0); 
		int month3 = getmonth(month2);		
		int year3 = getyear(year2,month3);
		request.setAttribute("monthindex", month3);
		request.setAttribute("yearindex", year3);

		JSPManager.showJSP(request, response, "/homecalendar.jsp");	
	}
	
	public int getmonth(int i)
    {
        i--;
        if(i < 1)
            i = 12;
        return i;
    }

    public int getyear(int i, int j)
    {
        if(j == 12)
            i--;
        return i;
    }
	
}

