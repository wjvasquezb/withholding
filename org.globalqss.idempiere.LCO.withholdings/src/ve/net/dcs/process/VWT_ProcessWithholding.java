package ve.net.dcs.process;

import java.util.List;

import org.compiere.model.Query;
import org.compiere.process.ProcessInfoParameter;
import org.compiere.process.SvrProcess;
import org.compiere.util.DB;
import org.globalqss.model.MLCOInvoiceWithholding;
import org.globalqss.model.X_LCO_InvoiceWithholding;

import ve.net.dcs.model.MLVEVoucherWithholding;

public class VWT_ProcessWithholding extends SvrProcess {
	private int p_Record_ID = 0;
	private String docAction = "";

	public VWT_ProcessWithholding() {
	}

	@Override
	protected void prepare() {
		ProcessInfoParameter[] para = getParameter();
		for (ProcessInfoParameter p : para) {
			String name = p.getParameterName();
			if (name == null)
				;
			else if (name.equals("DocAction"))
				docAction = p.getParameter().toString();
			else
				log.severe("Unknown Parameter: " + name);
		}
		p_Record_ID = getRecord_ID();

	}

	@Override
	protected String doIt() throws Exception {
		MLVEVoucherWithholding voucher = new MLVEVoucherWithholding(getCtx(), p_Record_ID, get_TrxName());

		if (docAction.equals("CO")){
			List<MLCOInvoiceWithholding> invoiceW = new Query(voucher.getCtx(), X_LCO_InvoiceWithholding.Table_Name, " LVE_VoucherWithholding_ID = ? ", voucher.get_TrxName()).setOnlyActiveRecords(true).setParameters(voucher.get_ID()).list();
			if (invoiceW.size() > 0){
				if(voucher.completeIt().equals(MLVEVoucherWithholding.DOCACTION_Complete))
				{
					DB.executeUpdate("UPDATE LVE_VoucherWithholding SET DocAction='CL',Processed='Y',DocStatus='CO' WHERE LVE_VoucherWithholding_ID = "+voucher.get_ID(),get_TrxName());
					return "@Completed@";
				}
				else
				{
					return voucher.getProcessMsg();
				}
			}else{
				return "El Comprobante no tiene Línea de Retenciones Asociadas.";
			}
		}
		else if (docAction.equals("VO")){
			if(voucher.voidIt())
			{
				DB.executeUpdate("UPDATE LVE_VoucherWithholding SET DocAction='--',C_Payment_ID=null,Processed='Y',DocStatus='VO' WHERE LVE_VoucherWithholding_ID = "+voucher.get_ID(),get_TrxName());
				return "@Voided@";
			}
			else
			{
				return voucher.getProcessMsg();
			}
		}
			
		else if (docAction.equals("RE"))
		{
			if(voucher.reActiveIt().equals(MLVEVoucherWithholding.DOCACTION_Re_Activate)) {
				DB.executeUpdate("UPDATE LVE_VoucherWithholding SET DocAction='CO',C_Payment_ID=null,Processed='N',DocStatus='DR' WHERE LVE_VoucherWithholding_ID = "+voucher.get_ID(),get_TrxName());
				return "@Success@";
			}
			else
			{
				return voucher.getProcessMsg();
			}
		}
		else
			return null;
	}

}
