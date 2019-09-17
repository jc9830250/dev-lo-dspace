package org.dspace.app.webui.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.dspace.app.webui.util.JSPManager;
import org.dspace.authorize.AuthorizeException;
import org.dspace.core.Context;


public class MonthAddServlet extends DSpaceServlet 
{
	public void doDSPost(Context context, HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException, SQLException, AuthorizeException
    {
                //choose用來判斷是要月份加1回傳值，或者直接回傳原值
                String choose = request.getParameter("choose");
                int choose2 = (request.getParameter("choose") != null ? Integer.parseInt(choose) : 0);

                String month1 = request.getParameter("addmonth");
		String year1 = request.getParameter("styear");
		int month2 = (request.getParameter("addmonth") != null ? Integer.parseInt(month1) : 0);
		int year2 = (request.getParameter("styear") != null ? Integer.parseInt(year1) : 0);

                if(choose2 != 1)
                {
                    int month3 = getmonth(month2);
                    int year3 = getyear(year2,month3);
                    request.setAttribute("monthindex", month3);
                    request.setAttribute("yearindex", year3);

                    JSPManager.showJSP(request, response, "/homecalendar.jsp");
                }
                else
                {
                    request.setAttribute("monthindex", month2);
                    request.setAttribute("yearindex", year2);
                    
                    JSPManager.showJSP(request, response, "/homecalendar.jsp");	
                }
    }
	
    public int getmonth(int i)
    {
        i++;
        if(i > 12)
            i = 1;
        return i;
    }

    public int getyear(int i, int j)
    {
        if(j == 1)
            i++;
        return i;
    }

	
}