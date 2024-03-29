/*
 * BrowseListTag.java
 *
 * Version: $Revision: 1.30 $
 *
 * Date: $Date: 2006/04/05 02:15:45 $
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
package org.dspace.app.webui.jsptag;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dspace.app.webui.util.UIUtil;
import org.dspace.browse.*;
import org.dspace.content.Bitstream;
import org.dspace.content.DCDate;
import org.dspace.content.DCValue;
import org.dspace.content.Item;
import org.dspace.content.Thumbnail;
import org.dspace.content.service.ItemService;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.core.Utils;
import org.dspace.storage.bitstore.BitstreamStorageManager;
import org.dspace.sort.SortOption;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.jstl.fmt.LocaleSupport;
import javax.servlet.jsp.tagext.TagSupport;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.util.StringTokenizer;

/**
 * Tag for display a list of items
 *
 * @author Robert Tansley
 * @version $Revision: 1.30 $
 */
public class BrowseListTag extends TagSupport
{
	 /** log4j category */
    private static Logger log = Logger.getLogger(BrowseListTag.class);

    /** Items to display */
    private BrowseItem[] items;

    /** Row to highlight, -1 for no row */
    private int highlightRow = -1;

    /** Column to emphasise, identified by metadata field */
    private String emphColumn;

    /** Config value of thumbnail view toggle */
    private boolean showThumbs;

    /** Config browse/search width and height */
    private int thumbItemListMaxWidth;

    private int thumbItemListMaxHeight;

    /** Config browse/search thumbnail link behaviour */
    private boolean linkToBitstream = false;

    /** Config to include an edit link */
    private boolean linkToEdit = false;

    /** Config to disable cross links */
    private boolean disableCrossLinks = false;

    /** The default fields to be displayed when listing items */
    private static String listFields;

    /** The default widths for the columns */
    private static String listWidths;

    /** The default field which is bound to the browse by date */
    //private static String dateField = "dc.date.issued";
    private static String dateField = ConfigurationManager.getProperty("webui.browse.date-issued-field", "dc.date.issued");

    /** The default field which is bound to the browse by title */
    //private static String titleField = "dc.title";
    private static String titleField = ConfigurationManager.getProperty("webui.browse.title-field", "dc.title");

    //private static String authorField = "dc.contributor.*";
    private static String authorField = ConfigurationManager.getProperty("webui.browse.author-field", "dc.contributor.*");

    private static int authorLimit = -1;

    private BrowseInfo browseInfo;

    public BrowseListTag()
    {
        super();
        getThumbSettings();

        if (showThumbs)
        {
            listFields = "thumbnail, dc.date.issued(date), dc.title, dc.contributor.*";
            listWidths = "*, 130, 60%, 40%";
        }
        else
        {
            listFields = "dc.date.issued(date), dc.title, dc.contributor.*";
            listWidths = "130, 60%, 40%";
        }
    }

    public int doStartTag() throws JspException
    {
        JspWriter out = pageContext.getOut();
        HttpServletRequest hrq = (HttpServletRequest) pageContext.getRequest();

        /* just leave this out now
        boolean emphasiseDate = false;
        boolean emphasiseTitle = false;

        if (emphColumn != null)
        {
            emphasiseDate = emphColumn.equalsIgnoreCase("date");
            emphasiseTitle = emphColumn.equalsIgnoreCase("title");
        }
        */

        // get the elements to display
        String browseListLine  = null;
        String browseWidthLine = null;

        // As different indexes / sort options may require different columns to be displayed
        // try to obtain a custom configuration based for the browse that has been performed
        if (browseInfo != null)
        {
            SortOption so = browseInfo.getSortOption();
            BrowseIndex bix = browseInfo.getBrowseIndex();

            // We have obtained the index that was used for this browse
            if (bix != null)
            {
                // First, try to get a configuration for this browse and sort option combined
                if (so != null && browseListLine == null)
                {
                    browseListLine  = ConfigurationManager.getProperty("webui.itemlist.browse." + bix.getName() + ".sort." + so.getName() + ".columns");
                    browseWidthLine = ConfigurationManager.getProperty("webui.itemlist.browse." + bix.getName() + ".sort." + so.getName() + ".widths");
                }

                // We haven't got a sort option defined, so get one for the index
                // - it may be required later
                if (so == null)
                    so = bix.getSortOption();
            }

            // If no config found, attempt to get one for this sort option
            if (so != null && browseListLine == null)
            {
                browseListLine  = ConfigurationManager.getProperty("webui.itemlist.sort." + so.getName() + ".columns");
                browseWidthLine = ConfigurationManager.getProperty("webui.itemlist.sort." + so.getName() + ".widths");
            }

            // If no config found, attempt to get one for this browse index
            if (bix != null && browseListLine == null)
            {
                browseListLine  = ConfigurationManager.getProperty("webui.itemlist.browse." + bix.getName() + ".columns");
                browseWidthLine = ConfigurationManager.getProperty("webui.itemlist.browse." + bix.getName() + ".widths");
            }

            // If no config found, attempt to get a general one, using the sort name
            if (so != null && browseListLine == null)
            {
                browseListLine  = ConfigurationManager.getProperty("webui.itemlist." + so.getName() + ".columns");
                browseWidthLine = ConfigurationManager.getProperty("webui.itemlist." + so.getName() + ".widths");
            }

            // If no config found, attempt to get a general one, using the index name
            if (bix != null && browseListLine == null)
            {
                browseListLine  = ConfigurationManager.getProperty("webui.itemlist." + bix.getName() + ".columns");
                browseWidthLine = ConfigurationManager.getProperty("webui.itemlist." + bix.getName() + ".widths");
            }
        }

        if (browseListLine == null)
        {
            browseListLine  = ConfigurationManager.getProperty("webui.itemlist.columns");
            browseWidthLine = ConfigurationManager.getProperty("webui.itemlist.widths");
        }

        // Have we read a field configration from dspace.cfg?
        if (browseListLine != null)
        {
            // If thumbnails are disabled, strip out any thumbnail column from the configuration
            if (!showThumbs && browseListLine.contains("thumbnail"))
            {
                // Ensure we haven't got any nulls
                browseListLine  = browseListLine  == null ? "" : browseListLine;
                browseWidthLine = browseWidthLine == null ? "" : browseWidthLine;

                // Tokenize the field and width lines
                StringTokenizer bllt = new StringTokenizer(browseListLine,  ",");
                StringTokenizer bwlt = new StringTokenizer(browseWidthLine, ",");

                StringBuilder newBLLine = new StringBuilder();
                StringBuilder newBWLine = new StringBuilder();
                while (bllt.hasMoreTokens() || bwlt.hasMoreTokens())
                {
                    String browseListTok  = bllt.hasMoreTokens() ? bllt.nextToken() : null;
                    String browseWidthTok = bwlt.hasMoreTokens() ? bwlt.nextToken() : null;

                    // Only use the Field and Width tokens, if the field isn't 'thumbnail'
                    if (browseListTok == null || !browseListTok.trim().equals("thumbnail"))
                    {
                        if (browseListTok != null)
                        {
                            if (newBLLine.length() > 0)
                                newBLLine.append(",");

                            newBLLine.append(browseListTok);
                        }

                        if (browseWidthTok != null)
                        {
                            if (newBWLine.length() > 0)
                                newBWLine.append(",");

                            newBWLine.append(browseWidthTok);
                        }
                    }
                }

                // Use the newly built configuration file
                browseListLine  = newBLLine.toString();
                browseWidthLine = newBWLine.toString();
            }

            listFields = browseListLine;
            listWidths = browseWidthLine;
        }

        // get the date and title fields
        String dateLine = ConfigurationManager.getProperty("webui.browse.index.date");
        if (dateLine != null)
        {
            dateField = dateLine;
        }

        String titleLine = ConfigurationManager.getProperty("webui.browse.index.title");
        if (titleLine != null)
        {
            titleField = titleLine;
        }

        // get the author truncation config
        String authorLine = ConfigurationManager.getProperty("webui.browse.author-field");
        if (authorLine != null)
        {
        	authorField = authorLine;
        }

        // Arrays used to hold the information we will require when outputting each row
        String[] fieldArr  = listFields == null ? new String[0] : listFields.split("\\s*,\\s*");
        String[] widthArr  = listWidths == null ? new String[0] : listWidths.split("\\s*,\\s*");
        boolean isDate[]   = new boolean[fieldArr.length];
        boolean emph[]     = new boolean[fieldArr.length];
        boolean isAuthor[] = new boolean[fieldArr.length];
        boolean viewFull[] = new boolean[fieldArr.length];
        String[] browseType = new String[fieldArr.length];
        String[] cOddOrEven = new String[fieldArr.length];

        try
        {
        	// Get the interlinking configuration too
            CrossLinks cl = new CrossLinks();

            // Get a width for the table
            String tablewidth = ConfigurationManager.getProperty("webui.itemlist.tablewidth");

            // If we have column widths, try to use a fixed layout table - faster for browsers to render
            // but not if we have to add an 'edit item' button - we can't know how big it will be
            if (widthArr.length > 0 && widthArr.length == fieldArr.length && !linkToEdit)
            {
                // If the table width has been specified, we can make this a fixed layout
                if (!StringUtils.isEmpty(tablewidth))
                { //套版註解
//                    out.println("<table style=\"width: " + tablewidth + "; table-layout: fixed;\" align=\"center\" class=\"miscTable\" summary=\"This table browses all dspace content\">");
                }
                else
                {
                    // Otherwise, don't constrain the width
//                    out.println("<table align=\"center\" class=\"miscTable\" summary=\"This table browses all dspace content\">"); //套版註解
                }

                // Output the known column widths
                out.print("<colgroup>");

                for (int w = 0; w < widthArr.length; w++)
                {
                    out.print("<col width=\"");

                    // For a thumbnail column of width '*', use the configured max width for thumbnails
                    if (fieldArr[w].equals("thumbnail") && widthArr[w].equals("*"))
                    {
                        out.print(thumbItemListMaxWidth);
                    }
                    else
                    {
                        out.print(StringUtils.isEmpty(widthArr[w]) ? "*" : widthArr[w]);
                    }

                    out.print("\" />");
                }

                out.println("</colgroup>");
            }
            else if (!StringUtils.isEmpty(tablewidth))
            {   //套版註解
//                out.println("<table width=\"" + tablewidth + "\" align=\"center\" class=\"miscTable\" summary=\"This table browses all dspace content\">");
            }
            else
            { //列表table //套版註解
//                out.println("<table align=\"center\" class=\"miscTable\" summary=\"This table browses all dspace content\">");
            }

            // Output the table headers
            out.println("<tr>");

            for (int colIdx = 0; colIdx < fieldArr.length; colIdx++)
            {
                //out.print("[colIdx1: " + Integer.toString(colIdx) + "]");
                
                String field = fieldArr[colIdx].toLowerCase().trim();
                cOddOrEven[colIdx] = (((colIdx + 1) % 2) == 0 ? "Odd" : "Even");

                // find out if the field is a date
                if (field.indexOf("(date)") > 0)
                {
                    field = field.replaceAll("\\(date\\)", "");
                    isDate[colIdx] = true;
                }

                // Cache any modifications to field
                fieldArr[colIdx] = field;

                // find out if this is the author column
                if (field.equals(authorField))
                {
                	isAuthor[colIdx] = true;
                }

                // find out if this field needs to link out to other browse views
                if (cl.hasLink(field))
                {
                	browseType[colIdx] = cl.getLinkType(field);
                	viewFull[colIdx] = BrowseIndex.getBrowseIndex(browseType[colIdx]).isItemIndex();
                }

                // find out if we are emphasising this field
                /*
                if ((field.equals(dateField) && emphasiseDate) ||
                        (field.equals(titleField) && emphasiseTitle))
                {
                    emph[colIdx] = true;
                }
                */
                if (field.equals(emphColumn))
                {
                	emph[colIdx] = true;
                }

                // prepare the strings for the header
                String id = "t" + Integer.toString(colIdx + 1);
                String css = "oddRow" + cOddOrEven[colIdx] + "Col";
                String message = "itemlist." + field;

                // output the header
                out.print("<th id=\"" + id +  "\" class=\"" + css + "\">"
                        + (emph[colIdx] ? "<strong>" : "")
                        + LocaleSupport.getLocalizedMessage(pageContext, message)
                        + (emph[colIdx] ? "</strong>" : "") + "</th>");
            }   // for (int colIdx = 0; colIdx < fieldArr.length; colIdx++)

            if (linkToEdit)
            {
                String id = "t" + Integer.toString(cOddOrEven.length + 1);
                String css = "oddRow" + cOddOrEven[cOddOrEven.length - 2] + "Col";

                // output the header
                out.print("<th id=\"" + id +  "\" class=\"" + css + "\">"
                        + (emph[emph.length - 2] ? "<strong>" : "")
                        + "&nbsp;" //LocaleSupport.getLocalizedMessage(pageContext, message)
                        + (emph[emph.length - 2] ? "</strong>" : "") + "</th>");
            }

            out.print("</tr>");

            // now output each item row
            for (int i = 0; i < items.length; i++)
            {
                //out.print("(items.length: " + Integer.toString(i) + ")");
                
                // now prepare the XHTML frag for this division
                String rOddOrEven;
                if (i == highlightRow)
                {
                    rOddOrEven = "highlight";
                }
                else
                {
                    rOddOrEven = ((i % 2) == 1 ? "odd" : "even");
                }
                
                //out.print("[a]");

                for (int colIdx = 0; colIdx < fieldArr.length; colIdx++)
                {
//                    out.print("[colIdx" + Integer.toString(colIdx) + "]");
                    
                    String field = fieldArr[colIdx];

                    // get the schema and the element qualifier pair
                    // (Note, the schema is not used for anything yet)
                    // (second note, I hate this bit of code.  There must be
                    // a much more elegant way of doing this.  Tomcat has
                    // some weird problems with variations on this code that
                    // I tried, which is why it has ended up the way it is)
                    StringTokenizer eq = new StringTokenizer(field, ".");
                    
//                    out.print("[a]");

                    String[] tokens = { "", "", "" };
                    int k = 0;
                    while(eq.hasMoreTokens())
                    {
                        tokens[k] = eq.nextToken().toLowerCase().trim();
                        k++;
                    }
                    
//                    out.print("[b]");
                    
                    String schema = tokens[0];
                    String element = tokens[1];
                    String qualifier = tokens[2];

                    // first get hold of the relevant metadata for this column
                    DCValue[] metadataArray;
                    if (qualifier.equals("*"))
                    {
                        metadataArray = items[i].getMetadata(schema, element, Item.ANY, Item.ANY);
                        
                    }
                    else if (qualifier.equals(""))
                    {
                        metadataArray = items[i].getMetadata(schema, element, null, Item.ANY);
                    }
                    else
                    {
                        metadataArray = items[i].getMetadata(schema, element, qualifier, Item.ANY);
                    }

                    // save on a null check which would make the code untidy
                    if (metadataArray == null)
                    {
                    	metadataArray = new DCValue[0];
                    }
                    
//                    out.print("[c]");

                    // now prepare the content of the table division
                    String metadata = "-";
//                    out.print("[d]");
                    
//                    if (field.equals(titleField) && metadata.equals("-"))
//out.println(metadata+" 4 "+element+ "  " +element.equals("title"));
                    if ( element.equals("title"))
                    {
                    	metadata = "<em>" + LocaleSupport.getLocalizedMessage(pageContext, "jsp.general.untitled") + "</em>";
                    	
//                    	if (items[i].isWithdrawn() == false)
//                    	{
//                    		 metadata = "<a href=\"" + hrq.getContextPath() + "/handle/"
//	                            + items[i].getHandle() + "\">"
//	                            + metadata
//	                            + "</a>6";
//                    	}
//                        out.println(metadata+"  1 "+element+ "  " +element.equals("title"));
                    }
                    
//                    out.print("[d]");
                    
                    if (field.equals("thumbnail"))
                    {
                        metadata = getThumbMarkup(hrq, items[i]);
//                        out.println(metadata+" 2.10  ");
                    }
                    else if (metadataArray.length > 0)
                    {
                        //out.print("[c]");
                        
                        // format the date field correctly
                        if (isDate[colIdx])
                        {
                            DCDate dd = new DCDate(metadataArray[0].value);
                            metadata = UIUtil.displayDate(dd, false, false, hrq);
//                            out.println(metadata+" 2.9  ");
                        }
                        // format the title field correctly for withdrawn items (ie. don't link)
                        else if (field.equals(titleField) && items[i].isWithdrawn())
                        {
                            metadata = Utils.addEntities(metadataArray[0].value, false);
                            if (metadata.equals("-"))
                    			metadata = "<em>" + LocaleSupport.getLocalizedMessage(pageContext, "jsp.general.untitled") + "</em>";
//                            out.println(metadata+" 2.1  ");
                        }
                        // format the title field correctly (as long as the item isn't withdrawn, link to it)
                        else if (field.equals(titleField))
                        {
                        	metadata = Utils.addEntities(metadataArray[0].value, false);
                            if (metadata.equals("-"))
                    			metadata = "<em>" + LocaleSupport.getLocalizedMessage(pageContext, "jsp.general.untitled") + "</em>";
                        	
                            metadata = "<a href=\"" + hrq.getContextPath() + "/handle/"
                            + items[i].getHandle() + "\">"
                            + metadata
                            + "</a>";
//                            out.println(metadata+" 2.2  ");
                        }
                        // format all other fields
                        else
                        {
//                            out.print("[e]");
                            
                            if (element.equals("title"))
                            {
                                metadata = "<em>" + LocaleSupport.getLocalizedMessage(pageContext, "jsp.general.untitled") + "</em>";

//                                if (items[i].isWithdrawn() == false)
//                                {
//                                         metadata = "<a href=\"" + hrq.getContextPath() + "/handle/"
//                                            + items[i].getHandle() + "\">"
//                                            + metadata
//                                            + "</a>";
//                                }
//                                out.println(metadata+"   "+element+ "  " +element.equals("title"));
                            }
                            
                        	// limit the number of records if this is the author field (if
                        	// -1, then the limit is the full list)
                        	boolean truncated = false;
                        	int loopLimit = metadataArray.length;
                        	if (isAuthor[colIdx])
                        	{
                        		int fieldMax = (authorLimit == -1 ? metadataArray.length : authorLimit);
                        		loopLimit = (fieldMax > metadataArray.length ? metadataArray.length : fieldMax);
                        		truncated = (fieldMax < metadataArray.length);
                        		log.debug("Limiting output of field " + field + " to " + Integer.toString(loopLimit) + " from an original " + Integer.toString(metadataArray.length));
//                                        out.println(metadata+" 2.3  ");
                        	}
//                            out.print("[f]");
                            StringBuffer sb = new StringBuffer();
                            
                            //sb.append("[1]");
                            
                            for (int j = 0; j < loopLimit; j++)
                            {
                            	String startLink = "";
                            	String endLink = "";
//                                out.print("[f1]");
                            	if (!StringUtils.isEmpty(browseType[colIdx]) && !disableCrossLinks)
                            	{
                            		String argument = "value";
                            		if (viewFull[colIdx])
                            		{
                            			argument = "vfocus";
//                                                out.println(metadata+" 2.7  ");
                            		}
                            		startLink = "<a href=\"" + hrq.getContextPath() + "/browse?type=" + browseType[colIdx] + "&amp;" +
                                            argument + "=" + Utils.addEntities(metadataArray[j].value);

                                    if (metadataArray[j].language != null)
                                    {
                                        startLink = startLink + "&amp;" +
                                            argument + "_lang=" + Utils.addEntities(metadataArray[j].language) +
                                            "\">";
//                                        out.println(metadata+" 2.8  ");
                                    }
                                    else
                                    {
                                        startLink = startLink + "\">";
                                    }
                            		endLink = "</a>";
//                                        out.println(metadata+" 2.4  ");
                            	}
                                //out.print("[f2]");
                            	sb.append(startLink);
                            	String metadataValue = Utils.addEntities(metadataArray[j].value, false).trim();
                                    // 葉初春 //<-加;
                                    // <div class="xml-root"></div> //<-不加;
                                    // 22
                                
                                
                                //out.print("[f3]");
                                String needle = "<div class=\"xml-root\">";
                                sb.append(metadataValue);
                                sb.append(endLink);
                                
                                if (j < (loopLimit - 1)) {
                                    if (metadataValue.length() > needle.length()-1) {
                                        if (metadataValue.substring(0, needle.length()).equals(needle) == false) {
                                            sb.append("; ");
                                        }
                                    }
                                    else {
                                        // metadataValue.length() < needle.length()
                                        sb.append("; ");
                                    }
                                }
                                //out.print("[f4]");
                            }   // for (int j = 0; j < loopLimit; j++)
                            //out.print("[g]");
                            //sb.append("[2]");
                            
                            if (truncated)
                            {
                            	String etal = LocaleSupport.getLocalizedMessage(pageContext, "itemlist.et-al");
                            	if (sb.toString().equals("") == false)
                            		sb.append(", ");
                            	 sb.append(etal);
//                                 out.println(metadata+" 2.6  ");
                            }
//                            out.print("[h]");
                            //sb.append("[3]");
                            
                            metadata = sb.toString() ;
                            
//                            out.print("[i]");
                            
                             if ( element.equals("title")){
                                metadata = "<a href=\"" + hrq.getContextPath() + "/handle/"
	                            + items[i].getHandle() + "\">"
	                            + metadata
	                            + "</a>";
                            }
                             
//                            out.print("[j]");
                        }
                        
                    }
                    // prepare extra special layout requirements for dates
                    String extras = "";
                    if (isDate[colIdx])
                    {
                        extras = "nowrap=\"nowrap\" align=\"right\"";
                    }
                    
                    // 確認 metadata 是否為string
                        // 如果不是，那就要給他metadata.toString();

                    String id = "t" + Integer.toString(colIdx + 1);
                    
                    String isThumbnail = "";
                    if (field.equals("thumbnail"))
                    	isThumbnail = " thumbnail";
                    //發布日期、題名與作者內容
                    String output = "<td headers=\"" + id + "\" class=\""
                    	+ rOddOrEven + "Row" + cOddOrEven[colIdx] + "Col"+isThumbnail+"\" " + extras + ">"
                    	+ (emph[colIdx] ? "<strong>" : "") + metadata + (emph[colIdx] ? "</strong>" : "")
                    	+ "</td>";
                    
//                    out.print("[last]");
                    
                    out.print(output);
                }

                // Add column for 'edit item' links
                if (linkToEdit)
                {
                    String id = "t" + Integer.toString(cOddOrEven.length + 1);

                    out.print("<td headers=\"" + id + "\" class=\""
                        + rOddOrEven + "Row" + cOddOrEven[cOddOrEven.length - 2] + "Col\" nowrap>"
                        + "<form method=get action=\"" + hrq.getContextPath() + "/tools/edit-item\">"
                        + "<input type=\"hidden\" name=\"handle\" value=\"" + items[i].getHandle() + "\" />"
                        + "<input type=\"submit\" value=\"Edit Item\" /></form>"
                        + "</td>");
                }

                out.println("</tr>");
            }

            // close the table
//            out.println("</table>"); //套版註解
        }
        catch (IOException ie)
        {
            throw new JspException(ie);
        }
        catch (SQLException e)
        {
        	throw new JspException(e);
        }
        catch (BrowseException e)
        {
        	throw new JspException(e);
        }

        return SKIP_BODY;
    }

    public BrowseInfo getBrowseInfo()
    {
    	return browseInfo;
    }

    public void setBrowseInfo(BrowseInfo browseInfo)
    {
    	this.browseInfo = browseInfo;
    	setItems(browseInfo.getBrowseItemResults());
    	authorLimit = browseInfo.getEtAl();
    }

    public boolean getLinkToEdit()
    {
        return linkToEdit;
    }

    public void setLinkToEdit(boolean edit)
    {
        this.linkToEdit = edit;
    }

    public boolean getDisableCrossLinks()
    {
        return disableCrossLinks;
    }

    public void setDisableCrossLinks(boolean links)
    {
        this.disableCrossLinks = links;
    }

    /**
     * Get the items to list
     *
     * @return the items
     */
    public BrowseItem[] getItems()
    {
        return items;
    }

    /**
     * Set the items to list
     *
     * @param itemsIn
     *            the items
     */
    public void setItems(BrowseItem[] itemsIn)
    {
        items = itemsIn;
    }

    /**
     * Get the row to highlight - null or -1 for no row
     *
     * @return the row to highlight
     */
    public String getHighlightrow()
    {
        return String.valueOf(highlightRow);
    }

    /**
     * Set the row to highlight
     *
     * @param highlightRowIn
     *            the row to highlight or -1 for no highlight
     */
    public void setHighlightrow(String highlightRowIn)
    {
        if ((highlightRowIn == null) || highlightRowIn.equals(""))
        {
            highlightRow = -1;
        }
        else
        {
            try
            {
                highlightRow = Integer.parseInt(highlightRowIn);
            }
            catch (NumberFormatException nfe)
            {
                highlightRow = -1;
            }
        }
    }

    /**
     * Get the column to emphasise - "title", "date" or null
     *
     * @return the column to emphasise
     */
    public String getEmphcolumn()
    {
        return emphColumn;
    }

    /**
     * Set the column to emphasise - "title", "date" or null
     *
     * @param emphColumnIn
     *            column to emphasise
     */
    public void setEmphcolumn(String emphColumnIn)
    {
        emphColumn = emphColumnIn;
    }

    public void release()
    {
        highlightRow = -1;
        emphColumn = null;
        items = null;
    }

    /* get the required thumbnail config items */
    private void getThumbSettings()
    {
        showThumbs = ConfigurationManager
                .getBooleanProperty("webui.browse.thumbnail.show");

        if (showThumbs)
        {
            thumbItemListMaxHeight = ConfigurationManager
                    .getIntProperty("webui.browse.thumbnail.maxheight");

            if (thumbItemListMaxHeight == 0)
            {
                thumbItemListMaxHeight = ConfigurationManager
                        .getIntProperty("thumbnail.maxheight");
            }

            thumbItemListMaxWidth = ConfigurationManager
                    .getIntProperty("webui.browse.thumbnail.maxwidth");

            if (thumbItemListMaxWidth == 0)
            {
                thumbItemListMaxWidth = ConfigurationManager
                        .getIntProperty("thumbnail.maxwidth");
            }
        }

        String linkBehaviour = ConfigurationManager
                .getProperty("webui.browse.thumbnail.linkbehaviour");

        if (linkBehaviour != null)
        {
            if (linkBehaviour.equals("bitstream"))
            {
                linkToBitstream = true;
            }
        }
    }

    /*
     * Get the (X)HTML width and height attributes. As the browser is being used
     * for scaling, we only scale down otherwise we'll get hideously chunky
     * images. This means the media filter should be run with the maxheight and
     * maxwidth set greater than or equal to the size of the images required in
     * the search/browse
     */
    private String getScalingAttr(HttpServletRequest hrq, Bitstream bitstream)
            throws JspException
    {
        BufferedImage buf;

        try
        {
            Context c = UIUtil.obtainContext(hrq);

            InputStream is = BitstreamStorageManager.retrieve(c, bitstream
                    .getID());

            //AuthorizeManager.authorizeAction(bContext, this, Constants.READ);
            // 	read in bitstream's image
            buf = ImageIO.read(is);
            is.close();
        }
        catch (SQLException sqle)
        {
            throw new JspException(sqle.getMessage());
        }
        catch (IOException ioe)
        {
            throw new JspException(ioe.getMessage());
        }

        // now get the image dimensions
        float xsize = (float) buf.getWidth(null);
        float ysize = (float) buf.getHeight(null);

        // scale by x first if needed
        if (xsize > (float) thumbItemListMaxWidth)
        {
            // calculate scaling factor so that xsize * scale = new size (max)
            float scale_factor = (float) thumbItemListMaxWidth / xsize;

            // now reduce x size and y size
            xsize = xsize * scale_factor;
            ysize = ysize * scale_factor;
        }

        // scale by y if needed
        if (ysize > (float) thumbItemListMaxHeight)
        {
            float scale_factor = (float) thumbItemListMaxHeight / ysize;

            // now reduce x size
            // and y size
            xsize = xsize * scale_factor;
            ysize = ysize * scale_factor;
        }

        StringBuffer sb = new StringBuffer("width=\"").append(xsize).append(
                "\" height=\"").append(ysize).append("\"");

        return sb.toString();
    }

    /* generate the (X)HTML required to show the thumbnail */
    private String getThumbMarkup(HttpServletRequest hrq, BrowseItem item)
            throws JspException
    {
    	try
    	{
            Context c = UIUtil.obtainContext(hrq);
            Thumbnail thumbnail = ItemService.getThumbnail(c, item.getID(), linkToBitstream);

            if (thumbnail == null)
    		{
    			return "";
    		}
        	StringBuffer thumbFrag = new StringBuffer();

        	if (linkToBitstream)
        	{
        		Bitstream original = thumbnail.getOriginal();
        		String link = hrq.getContextPath() + "/bitstream/" + item.getHandle() + "/" + original.getSequenceID() + "/" +
        						UIUtil.encodeBitstreamName(original.getName(), Constants.DEFAULT_ENCODING);
        		thumbFrag.append("<a target=\"_blank\" href=\"" + link + "\" />");
        	}
        	else
        	{
        		String link = hrq.getContextPath() + "/handle/" + item.getHandle();
        		thumbFrag.append("<a href=\"" + link + "\" />");
        	}

        	Bitstream thumb = thumbnail.getThumb();
        	String img = hrq.getContextPath() + "/retrieve/" + thumb.getID() + "/" +
        				UIUtil.encodeBitstreamName(thumb.getName(), Constants.DEFAULT_ENCODING);
        	String alt = thumb.getName();
            String scAttr = getScalingAttr(hrq, thumb);
            thumbFrag.append("<img src=\"")
                     .append(img)
                     .append("\" alt=\"")
                     .append(alt + "\" ")
                     .append(scAttr)
                     .append("/ border=\"0\"></a>");

        	return thumbFrag.toString();
        }
        catch (SQLException sqle)
        {
        	throw new JspException(sqle.getMessage());
        }
        catch (UnsupportedEncodingException e)
        {
            throw new JspException("Server does not support DSpace's default encoding. ", e);
        }
    }
}
