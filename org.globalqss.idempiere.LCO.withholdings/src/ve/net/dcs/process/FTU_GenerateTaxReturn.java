package ve.net.dcs.process;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.compiere.model.MBPartner;
import org.compiere.model.MCharge;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.MInvoiceLine;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.globalqss.model.MLCOWithholdingType;

public class FTU_GenerateTaxReturn extends SvrProcess {
	
	/** The Record						*/
	private int withholdingType = 0;
	private int orgId = 0;
	private Timestamp dateDoc;
	private Timestamp dateFrom;
	private Timestamp dateFromTo;
	private int currencyId = 0;
	private int conversionTypeId = 0;
	private String docAction ;

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
			else if (name.equals("DateDoc"))
				dateDoc = (Timestamp)para[i].getParameter();
			else if (name.equals("DateFrom")) {
				dateFrom = (Timestamp)para[i].getParameter();
				dateFromTo = (Timestamp)para[i].getParameter_To();
			}
			else if (name.equals("AD_Org_ID"))
				orgId =Integer.parseInt(para[i].getParameter().toString());

			else if (name.equals("C_Currency_ID"))
				currencyId =Integer.parseInt(para[i].getParameter().toString());
			else if (name.equals("C_ConversionType_ID"))
				conversionTypeId =Integer.parseInt(para[i].getParameter().toString());
			else if (name.equals("DocAction"))
				docAction =para[i].getParameter().toString();
			else
				log.severe("Unknown Parameter: " + name);
		}
		//p_Record_ID =  getRecord_ID(); 
		
	}

	@Override
	protected String doIt() throws Exception {
		// TODO Auto-generated method stub
		
		
		String sql ="SELECT vw.AD_Org_ID, vw.LVE_VoucherWithholding_ID, vw.WithholdingNo,COALESCE(SUM(iw.TaxAmt),0) as TaxAmt "
					+ " FROM LVE_VoucherWithholding vw "
					+ " INNER join LCO_InvoiceWithholding iw on iw.lve_voucherwithholding_id = vw.lve_voucherwithholding_id "
					+ " WHERE vw.docstatus IN ('CO') AND vw.datetrx BETWEEN '"+dateFrom+"' AND '"+dateFromTo+"' AND vw.LCO_WithholdingType_ID="+withholdingType+" AND "
							+ "vw.ad_org_id in (SELECT DISTINCT Node_ID FROM getnodes("+orgId+"," + 
							" (SELECT AD_Tree_ID FROM AD_Tree WHERE TreeType ='OO' AND AD_Client_ID="+getAD_Client_ID()+"),"+getAD_Client_ID()+") AS N (Parent_ID numeric,Node_ID numeric) " + 
							" WHERE Parent_ID = "+orgId+")"
							+ "AND "
							+ " NOT EXISTS (SELECT 1 FROM c_invoiceline ci INNER JOIN c_invoice c ON c.c_invoice_id = ci.c_invoice_id"
							+ " WHERE ci.LVE_VoucherWithholding_ID = vw.LVE_VoucherWithholding_ID AND c.docstatus NOT IN ('RE','VO'))"
					+ " GROUP BY vw.AD_Org_ID,vw.LVE_VoucherWithholding_ID,vw.WithholdingNo ";
		
		MLCOWithholdingType withHoldingType = new MLCOWithholdingType(getCtx(),withholdingType,get_TrxName());
		
		int docTypeId = (int)withHoldingType.get_Value("C_DocTypeInvoice_ID");
		int cBPartnerId = withHoldingType.get_ValueAsInt("C_BPartner_ID");
		int chargeId = withHoldingType.get_ValueAsInt("C_Charge_ID");
		MCharge charge = new MCharge(getCtx(),chargeId,get_TrxName());
		

		MBPartner partner = new MBPartner(getCtx(),cBPartnerId,get_TrxName());
		
		MDocType docType = new MDocType(getCtx(),docTypeId,get_TrxName());
		boolean iSOTrx= docType.isSOTrx() ;
		
		
		String sqlbpd = "SELECT C_BPartner_Location_ID FROM C_BPartner_Location WHERE IsBillTo='Y' AND C_BPartner_ID="+cBPartnerId;
		
		int bpLocatorId = DB.getSQLValue(get_TrxName(), sqlbpd);
		
		String sqlcuom = "SELECT C_UOM_ID FROM C_UOM WHERE X12DE355='EA'";
		
		int C_UOM_ID = DB.getSQLValue(get_TrxName(), sqlcuom);
		
		String sqltax = "SELECT C_Tax_ID FROM C_Tax WHERE IsDefault='Y' AND C_TaxCategory_ID="+charge.getC_TaxCategory_ID();
		
		int C_Tax_ID = DB.getSQLValue(get_TrxName(), sqltax);
		
		
		MInvoice invoice = new MInvoice(getCtx(), 0, get_TrxName());
		invoice.setAD_Org_ID(getOrgTaxDeclare(orgId));
		invoice.setC_BPartner_ID(cBPartnerId);
		invoice.setC_BPartner_Location_ID(bpLocatorId);
		invoice.setC_DocType_ID(docTypeId);
		invoice.setC_DocTypeTarget_ID(docTypeId);
		invoice.setDateAcct(dateDoc);
		invoice.setDateInvoiced(dateDoc);
		invoice.setIsSOTrx(iSOTrx);
		int C_Currency_ID = (currencyId > 0 ? currencyId : Env.getContextAsInt(getCtx(), "$C_Currency_ID"));
		invoice.setC_Currency_ID(C_Currency_ID);
		invoice.setC_ConversionType_ID(conversionTypeId);
		invoice.setPaymentRule("T");
		invoice.setC_PaymentTerm_ID(partner.getPO_PaymentTerm_ID());
		invoice.setAD_User_ID(getAD_User_ID());
		invoice.saveEx(get_TrxName());
		
		invoice.set_ValueOfColumn("LVE_POInvoiceNo", invoice.getDocumentNo());
		invoice.saveEx(get_TrxName());
		
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		int cont = 0;
		
		try {
			
			pstmt= DB.prepareStatement(sql, get_TrxName());
			rs =  pstmt.executeQuery();
			
			while(rs.next()) {
				
				MInvoiceLine invoiceLine = new MInvoiceLine(getCtx(),0,get_TrxName());
				invoiceLine.setC_Invoice_ID(invoice.get_ID());
				invoiceLine.setAD_Org_ID(rs.getInt("AD_Org_ID"));
				invoiceLine.setC_Charge_ID(chargeId);
				invoiceLine.setQty(BigDecimal.ONE);
				invoiceLine.setQtyEntered(BigDecimal.ONE);
				invoiceLine.setQtyInvoiced(BigDecimal.ONE);
				invoiceLine.setPrice(rs.getBigDecimal("TaxAmt"));
				invoiceLine.setC_UOM_ID(C_UOM_ID);
				invoiceLine.setC_Tax_ID(C_Tax_ID);
				invoiceLine.setDescription("Comprobante de Retencion No:"+rs.getString("WithholdingNo"));
				invoiceLine.set_ValueOfColumn("LVE_VoucherWithholding_ID", rs.getInt("LVE_VoucherWithholding_ID"));
				invoiceLine.saveEx(get_TrxName());
				cont =+ 1;
			}
		
		}catch(Exception e) {
			
		}finally
		{
			DB.close(rs);
			rs = null;
			pstmt = null;
		}
					
		if(cont>0) {
			invoice.processIt(docAction);
			invoice.saveEx(get_TrxName());
		}else {
			invoice.deleteEx(true, get_TrxName());
			return "No se encontraron comprobantes validos para la declaracion";
		}
		
		String msg = Msg.parseTranslation(getCtx(), "@C_Invoice_ID@");
		addBufferLog(invoice.get_ID(), new Timestamp(System.currentTimeMillis()), null, msg+": "+invoice.getDocumentNo(), invoice.get_Table_ID(), invoice.get_ID());
		
		return "@OK@";
	}
	
	private int getOrgTaxDeclare(int orgId)
	{
		int orgID = 0;
		
		orgID = DB.getSQLValue(get_TrxName(), "SELECT AD_Org_ID FROM AD_OrgInfo WHERE IsOrgTaxDeclare = 'Y' AND Parent_Org_ID=?", orgId);
		
		if(orgID <=0)
		{
			orgID = orgId;
		}
		
		return orgID;
	}

}
