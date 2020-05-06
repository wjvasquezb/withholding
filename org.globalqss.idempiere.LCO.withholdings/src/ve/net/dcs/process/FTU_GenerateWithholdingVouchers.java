package ve.net.dcs.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.globalqss.model.LCO_MInvoice;
import org.globalqss.model.MLCOWithholdingType;

import ve.net.dcs.model.MLVEVoucherWithholding;
import ve.net.dcs.model.VWT_MInvoice;

public class FTU_GenerateWithholdingVouchers extends SvrProcess {
	
	/** The Record						*/
	private int withholdingType = 0;
	private int bPartnerId= 0;
	private int invoiceId = 0;
	private int orgId = 0;
	private Timestamp dateDocFrom;
	private Timestamp dateDocTo;
	private Timestamp dateTrx;
	private int cnt = 0;

	@Override
	protected void prepare() {
		// TODO Auto-generated method stub
		
		ProcessInfoParameter[] para = getParameter();
		for (int i = 0; i < para.length; i++)
		{
			String name = para[i].getParameterName();
			if (para[i].getParameter() == null)
				;			
			else if (name.equals("LCO_WithholdingType_ID"))
				withholdingType = Integer.parseInt(para[i].getParameter().toString());
			else if (name.equals("DateDoc")) {
				dateDocFrom = (Timestamp)para[i].getParameter();
				dateDocTo = (Timestamp)para[i].getParameter_To();
				}
			else if (name.equals("DateTrx"))
				dateTrx = (Timestamp)para[i].getParameter();
			else if (name.equals("C_BPartner_ID"))
				bPartnerId = Integer.parseInt(para[i].getParameter().toString());
			else if (name.equals("C_Invoice_ID"))
				invoiceId = Integer.parseInt(para[i].getParameter().toString());
			else if (name.equals("AD_Org_ID"))
				orgId =Integer.parseInt(para[i].getParameter().toString());
			else
				log.severe("Unknown Parameter: " + name);
		}
		//p_Record_ID =  getRecord_ID(); 
		
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		
		
		String sqlwt = "select dt.issotrx ,wt.LCO_WithholdingType_ID,wt.Type from LCO_WithholdingType wt "
				+" inner join c_doctype dt on wt.c_doctype_id = dt.c_doctype_id where dt.issotrx='N' and wt.isactive = 'Y' ";
		if(withholdingType>0) 
			sqlwt += " and wt.LCO_WithholdingType_ID="+withholdingType;
		
		PreparedStatement pstmtwt = null;
		ResultSet rswt = null;
		
		try {
			
			pstmtwt= DB.prepareStatement(sqlwt, get_TrxName());
			rswt =  pstmtwt.executeQuery();
			
			while(rswt.next()) {
				int withholdingType = rswt.getInt("LCO_WithholdingType_ID");
				boolean iSOTrx = rswt.getBoolean("issotrx");
				String type = rswt.getString("Type");
				String sql = "" ;
				
				if(type.equalsIgnoreCase("IVA")){
					
					sql = "SELECT i.c_bpartner_id"
							+ " FROM c_invoice i "
							+ " WHERE NOT EXISTS (SELECT 1 FROM lco_invoicewithholding iw WHERE iw.c_invoice_id = i.c_invoice_id AND iw.lco_withholdingtype_id = "+withholdingType+")"
							+ " AND i.ad_org_id="+orgId+" AND i.dateinvoiced BETWEEN '"+dateDocFrom+"' AND '"+dateDocTo+"' ";
					
				}else if(type.equalsIgnoreCase("ISLR")) {
					
					sql = "SELECT i.c_bpartner_id"
							+ " FROM c_invoice i "
							+ " inner join c_invoiceline il on i.c_invoice_id = il.c_invoice_id "
							+ " inner join c_charge c on il.c_charge_id = c.c_charge_id "
							+ " WHERE NOT EXISTS (SELECT 1 FROM lco_invoicewithholding iw WHERE iw.c_invoice_id = i.c_invoice_id AND iw.lco_withholdingtype_id = "+withholdingType+")"
							+ " AND i.ad_org_id="+orgId+" AND i.dateinvoiced BETWEEN '"+dateDocFrom+"' AND '"+dateDocTo+"' and c.LCO_WithholdingCategory_ID > 0";
					
				}else if(type.equalsIgnoreCase("IAE")) {
					
					sql = "SELECT i.c_bpartner_id"
							+ " FROM c_invoice i "
							+ " inner join C_BPartner bp on bp.c_bpartner_id = i.c_bpartner_id "
							+ " WHERE NOT EXISTS (SELECT 1 FROM lco_invoicewithholding iw WHERE iw.c_invoice_id = i.c_invoice_id AND iw.lco_withholdingtype_id = "+withholdingType+")"
							+ " AND i.ad_org_id="+orgId+" AND i.dateinvoiced BETWEEN '"+dateDocFrom+"' AND '"+dateDocTo+"' and bp.LCO_ISIC_ID > 0";
					
				}
				
				
				
				if(bPartnerId>0)
					sql = sql+" AND i.C_BPartner_ID="+bPartnerId;
				if(invoiceId>0)
					sql = sql+" AND i.C_Invoice_ID="+invoiceId;
				
				sql = sql+" GROUP BY i.c_bpartner_id";
				
				/*MLCOWithholdingType withHoldingType = new MLCOWithholdingType(getCtx(),withholdingType,get_TrxName());
				
				int docTypeId = withHoldingType.get_ValueAsInt("C_DocType_ID");
				
				MDocType docType = new MDocType(getCtx(),docTypeId,get_TrxName());
				boolean iSOTrx= docType.isSOTrx() ;*/
				
				PreparedStatement pstmt = null;
				ResultSet rs = null;
				
				try {
					
					pstmt= DB.prepareStatement(sql, get_TrxName());
					rs =  pstmt.executeQuery();
					
					while(rs.next()) {
						int cBPartnerId = rs.getInt("c_bpartner_id");
						
						MLVEVoucherWithholding voucher = new MLVEVoucherWithholding(getCtx(), 0, get_TrxName());
						voucher.setAD_Org_ID(orgId);
						voucher.setC_BPartner_ID(cBPartnerId);
						voucher.setDateFrom(dateDocFrom);
						voucher.setDateTo(dateDocTo);
						voucher.setDateTrx(dateTrx);
						voucher.setLCO_WithholdingType_ID(withholdingType);
						voucher.setIsSOTrx(iSOTrx);
						
						if(invoiceId>0)
							voucher.setC_Invoice_ID(invoiceId);
		
						
						voucher.saveEx(get_TrxName());
						
						MInvoice[] invoices = VWT_MInvoice.getOfBPartnerDateFromDateTo(getCtx(), voucher, get_TrxName());
						
						for (MInvoice mInvoice : invoices) {	
							log.info("Prueba "+mInvoice.getDateAcct());
							
							VWT_MInvoice invoice = new VWT_MInvoice(getCtx(), mInvoice.getC_Invoice_ID(), get_TrxName());
							cnt += invoice.recalcWithholdings(voucher);
						}
					}
				
				}catch(Exception e) {
					
				}finally
				{
					DB.close(rs);
					rs = null;
					pstmt = null;
				}
							
			}
			
		}catch(Exception e) {
			
		}finally
		{
			DB.close(rswt);
			rswt = null;
			pstmtwt = null;
		}
		
		return "Retenciones Generadas: "+cnt;
	}

}
