/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * Copyright (C) 1999-2006 ComPiere, Inc. All Rights Reserved.                *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * ComPiere, Inc., 2620 Augustine Dr. #245, Santa Clara, CA 95054, USA        *
 * or via info@compiere.org or http://www.compiere.org/license.html           *
 *****************************************************************************/
package ve.net.dcs.process;


import java.util.logging.Level;

import org.compiere.print.MPrintFormat;
import org.compiere.print.MPrintFormatItem;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;

/**
 * 	Copy Translations
 *	
 * */
public class CreateTemplateFromPrintFormat extends SvrProcess
{
	
	
	
	int p_AD_PrintFormat_ID = 0;
	
	protected void prepare()
	{
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (name.equals("AD_PrintFormat_ID"))
				p_AD_PrintFormat_ID = para[i].getParameterAsInt();
			else
				log.log(Level.SEVERE, "Unknown Parameter: " + name);
		}
	}	//	prepare
	
	/**
	 * 	Process
	 *	@return info
	 *	@throws Exception
	 */
	protected String doIt() throws Exception
	{	
		
		//MPrintFormat formatBase = new MPrintFormat(getCtx(),p_AD_PrintFormat_ID,get_TrxName());
		MPrintFormat template = createTemplate(p_AD_PrintFormat_ID);
		
		return template.toString();
	}	//	doit
	
	MPrintFormat createTemplate(int AD_PrintFormat_ID) {
		
		MPrintFormat template = MPrintFormat.copyToClient(getCtx(), AD_PrintFormat_ID, 0);
		
		for (MPrintFormatItem item:template.getAllItems()) {
			if(item.getPrintFormatType().equals("P")) {
				int childId = item.getAD_PrintFormatChild_ID();
				if(childId>0) {
					MPrintFormat child = createTemplate(childId);
					if(child!=null) {
						child.saveEx(get_TrxName());
					}
				}				
				
			}
		}
		template.setName("** TEMPLATE - "+template.getName()+" **");
		template.saveEx(get_TrxName());
		
		return template;
	}
	
	
	
}	//	CreateTemplateFromPrintFormat
