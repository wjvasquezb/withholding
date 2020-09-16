package ve.net.dcs.component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import org.adempiere.base.event.AbstractEventHandler;
import org.adempiere.base.event.IEventTopics;
import org.adempiere.exceptions.AdempiereException;
import org.compiere.model.I_C_BPartner;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.I_M_InOut;
import org.compiere.model.I_M_Movement;
import org.compiere.model.MDocType;
import org.compiere.model.MInvoice;
import org.compiere.model.PO;
import org.compiere.model.Query;
import org.compiere.model.X_C_BPartner;
import org.compiere.process.DocAction;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.globalqss.model.MLCOInvoiceWithholding;
import org.globalqss.model.MLCOWithholdingType;
import org.globalqss.model.X_LCO_InvoiceWithholding;
import org.osgi.service.event.Event;

import ve.net.dcs.model.I_LVE_VoucherWithholding;
import ve.net.dcs.model.MLVEVoucherWithholding;

public class VWTModelValidator extends AbstractEventHandler {
	
	private static CLogger log = CLogger.getCLogger(VWTModelValidator.class);
	/* TRANSFER CODE TO net.frontuari.lvedocumentcontrol BY JORGE COLMENAREZ
	/**	Current Business Partner				*/
	/*
	private int m_Current_C_BPartner_ID 		= 	0;
	/**	Current Allocation						*/
	/*
	private MAllocationHdr m_Current_Alloc 		= 	null;
	*/

	@Override
	protected void initialize() {
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_VOID, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REACTIVATE, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSEACCRUAL, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_REVERSECORRECT, I_C_Invoice.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_NEW, I_C_BPartner.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_CHANGE, I_C_BPartner.Table_Name);
		registerTableEvent(IEventTopics.PO_AFTER_CHANGE, MLVEVoucherWithholding.Table_Name);
		registerTableEvent(IEventTopics.PO_BEFORE_NEW, MLVEVoucherWithholding.Table_Name);
		registerTableEvent(IEventTopics.DOC_AFTER_COMPLETE, I_LVE_VoucherWithholding.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_M_Movement.Table_Name);
		registerTableEvent(IEventTopics.DOC_BEFORE_COMPLETE, I_M_InOut.Table_Name);
	}

	@Override
	protected void doHandleEvent(Event event) {

		PO po = getPO(event);
		String type = event.getTopic();
		log.info(po.get_TableName() + " Type: " + type);
		
		if (po.get_TableName().equals(MLVEVoucherWithholding.Table_Name) ) {
			
			MLVEVoucherWithholding voucher = (MLVEVoucherWithholding) po;
			
			if(type.equals(IEventTopics.PO_AFTER_CHANGE)){
				if(validateWithholdingNo(voucher)){
					String sqlwhere = " LVE_VoucherWithholding_ID = ?";
					List<MLCOInvoiceWithholding> invoiceW = new Query(po.getCtx(), X_LCO_InvoiceWithholding.Table_Name, sqlwhere, po.get_TrxName()).setOnlyActiveRecords(true).setParameters(voucher.get_ID()).list();
					
					for (MLCOInvoiceWithholding mlcoInvoiceWithholding : invoiceW) {
						mlcoInvoiceWithholding.setDateAcct((Timestamp)voucher.get_Value("DateAcct"));
						mlcoInvoiceWithholding.setDateTrx(voucher.getDateTrx());
						mlcoInvoiceWithholding.saveEx();
					}
				}
			}
			if(type.equals(IEventTopics.PO_BEFORE_NEW)){
				validateWithholdingNo(voucher);

			}
			    
		
		}
		
		if (po.get_TableName().equals(I_C_BPartner.Table_Name) && (type.equals(IEventTopics.PO_BEFORE_CHANGE) || type.equals(IEventTopics.PO_BEFORE_NEW))) {
			X_C_BPartner partner = (X_C_BPartner) po;
			if(partner.is_ValueChanged("TaxID")){
				int value = 0;
				String cadena = partner.getTaxID();
				
				// Se Corrige Validación, tomar en cuenta Grupo Empresarial.
				//	@contributor Ing. Victor Suárez - victor.suarez.is@gmail.com - 2016/11
				//value = DB.getSQLValue(partner.get_TrxName(), "SELECT 1 FROM C_BPartner WHERE LCO_taxIDType_ID = ? AND TaxID = ? AND C_BPartner_ID != ?", partner.get_ValueAsInt("LCO_TaxIDType_ID"),cadena,partner.get_ID());
				value = DB.getSQLValue(partner.get_TrxName(), "SELECT 1 FROM C_BPartner WHERE LCO_taxIDType_ID = ? AND TaxID = ? AND C_BPartner_ID != ? AND AD_Client_ID = ? ", partner.get_ValueAsInt("LCO_TaxIDType_ID"),cadena,partner.get_ID(), partner.getAD_Client_ID());
				
				if (value > 0)
					throw new RuntimeException("Tercero Ya Existe");
				partner.setTaxID(cadena);
			}
		}
		

		if (po.get_TableName().equals(I_C_BPartner.Table_Name) && type.equals(IEventTopics.PO_BEFORE_CHANGE)) {
			if(((X_C_BPartner) po).getTaxID()!=null){
				if(!((X_C_BPartner) po).getTaxID().equals("")){
					if (((X_C_BPartner) po).getTaxID().replaceAll("[\\w\\-]+","").matches("[\\W\\s]+") ){
						throw new RuntimeException("Caracteres no válidos en número de identificación");
					}
				}
			}
		} else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && type.equals(IEventTopics.DOC_AFTER_COMPLETE)) {

			MInvoice invoice = (MInvoice) po;
			/* TRANSFER CODE TO net.frontuari.lvedocumentcontrol BY JORGE COLMENAREZ
			if(!invoice.isSOTrx() && !(invoice.getDocumentNo().equals(invoice.get_ValueAsString("LVE_POInvoiceNo"))) 
					&& invoice.get_ValueAsString("LVE_POInvoiceNo") != null)
			{
				String reverse = (invoice.isReversal() ? "^" : "");
				if(invoice.isReversal())
				{
					invoice.set_ValueOfColumn("LVE_POInvoiceNo",invoice.get_ValueAsString("LVE_POInvoiceNo")+reverse);	
				}
				invoice.setDocumentNo(invoice.get_ValueAsString("LVE_POInvoiceNo"));
				invoice.saveEx(po.get_TrxName());
			}
			*/
			if (!invoice.isReversal()){
				String sqlwhere = " C_Invoice_ID = ? AND LVE_VoucherWithholding_ID IS NULL";
				List<MLCOInvoiceWithholding> invoiceW = new Query(po.getCtx(), X_LCO_InvoiceWithholding.Table_Name, sqlwhere, po.get_TrxName()).setOnlyActiveRecords(true).setParameters(invoice.get_ID()).setOrderBy("LCO_WithholdingType_ID").list();

				int LCO_WithholdingType_ID = 0;
				MLVEVoucherWithholding voucher = null;
				List<MLVEVoucherWithholding> listVoucher = new ArrayList<MLVEVoucherWithholding>();

				for (MLCOInvoiceWithholding iw : invoiceW) {
					if (LCO_WithholdingType_ID != iw.getLCO_WithholdingType_ID()) {
						LCO_WithholdingType_ID = iw.getLCO_WithholdingType_ID();
						voucher = new MLVEVoucherWithholding(po.getCtx(), 0, po.get_TrxName());
						voucher.setAD_Org_ID(po.getAD_Org_ID());
						voucher.set_ValueOfColumn("AD_Client_ID", po.getAD_Client_ID());
						voucher.set_ValueOfColumn("DateAcct", invoice.getDateAcct());
						voucher.setDateTrx(invoice.getDateAcct());
						voucher.setC_BPartner_ID(invoice.getC_BPartner_ID());
						voucher.setLCO_WithholdingType_ID(LCO_WithholdingType_ID);
						voucher.setC_Invoice_ID(iw.getC_Invoice_ID());
						voucher.setIsSOTrx(invoice.isSOTrx());
						MDocType doctype = new Query(po.getCtx(), MDocType.Table_Name, "C_DocType_ID IN (SELECT C_DocType_ID FROM LCO_WithholdingType WHERE LCO_WithholdingType_ID = "+iw.getLCO_WithholdingType_ID()+" ) ", po.get_TrxName()).first();
						voucher.set_ValueOfColumn("C_DocType_ID", doctype.getC_DocType_ID());
						voucher.saveEx();
						listVoucher.add(voucher);
					}

					iw.set_ValueOfColumn("LVE_VoucherWithholding_ID", voucher.get_ID());
					iw.saveEx();
				}

				for (MLVEVoucherWithholding v : listVoucher) {
				/** Si es Factura de ventas, solo preparar el comprobante, para que el usuario agregue el número del comprobante que le envíe el cliente.
				 *	@contributor: Ing. Victor Suárez - victor.suarez.is@gmail.com - 2016/11 
				 */
					if(invoice.isSOTrx())
						v.prepareIt();
					else
						v.completeIt();
					v.saveEx();
				}
			}
			
			/**
			 * Automatic Allocation Between Credit/Debit Notes with DocAffected
			 * @author Jorge Colmenarez <mailto:jcolmenarez@frontuari.net>, 2020-04-30 09:34
			 */
			/* TRANSFER CODE TO net.frontuari.lvedocumentcontrol BY JORGE COLMENAREZ
			MDocType m_DocType = (MDocType) invoice.getC_DocTypeTarget();
			if(!invoice.isPaid() 
					|| invoice.getReversal_ID() == 0
					|| m_DocType.get_ValueAsBoolean("IsAutoAllocation"))
				AutomaticAllocation(invoice);
			*/

		} else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && (type.equals(IEventTopics.DOC_BEFORE_VOID) || type.equals(IEventTopics.DOC_BEFORE_REACTIVATE) || type.equals(IEventTopics.DOC_BEFORE_REVERSEACCRUAL) || type.equals(IEventTopics.DOC_BEFORE_REVERSECORRECT))) {
			MInvoice invoice = (MInvoice) po;
			
			List<MLCOInvoiceWithholding> list = new Query(invoice.getCtx(), MLCOInvoiceWithholding.Table_Name, " C_Invoice_ID = ? ", invoice.get_TrxName()).setParameters(invoice.getC_Invoice_ID()).setOrderBy(MLCOInvoiceWithholding.COLUMNNAME_C_Invoice_ID).list();
			
			if (list.size() > 0){
				for (MLCOInvoiceWithholding mlcoInvoiceWithholding : list) {
					if (mlcoInvoiceWithholding.get_ValueAsInt("LVE_VoucherWithholding_ID") > 0){
						MLVEVoucherWithholding vw = new MLVEVoucherWithholding(invoice.getCtx(), mlcoInvoiceWithholding.get_ValueAsInt("LVE_VoucherWithholding_ID"), invoice.get_TrxName());
						if (vw.getDocStatus().equalsIgnoreCase(DocAction.STATUS_Completed) || vw.getDocStatus().equalsIgnoreCase(DocAction.STATUS_Drafted)){
							throw new AdempiereException("RETENCIÓN. "+vw.getWithholdingNo()+", Debe Anular primero la Retención Asociada");
						}
					}else{
						mlcoInvoiceWithholding.deleteEx(true);
					}
				}
			}
			
			ValidateDeclarationDocument(invoice);
		}/*  TRANSFER CODE TO net.frontuari.lvedocumentcontrol BY JORGE COLMENAREZ
		else if (po.get_TableName().equals(I_C_Invoice.Table_Name) && type.equals(IEventTopics.DOC_BEFORE_COMPLETE)) {

			String msgExistCN = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgExistINo = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_POInvoiceNo");
			String msgMandataryCN = Msg.translate(Env.getCtx(), "FillMandatory") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgMandataryINo = Msg.translate(Env.getCtx(), "FillMandatory") + ": " + Msg.getElement(Env.getCtx(), "LVE_POInvoiceNo");
			String msgSeqNotFound = Msg.translate(Env.getCtx(), "SequenceDocNotFound") + " " + Msg.getElement(Env.getCtx(), "LVE_ControlNoSequence_ID");

			String where = "AD_Org_ID=? AND C_BPartner_ID=? AND C_Invoice_ID!=? AND IsSOTrx=? AND DocStatus IN ('CO','CL') ";
			MInvoice invoice = (MInvoice) po;
			MDocType docType = (MDocType) invoice.getC_DocType();

			if (invoice.getReversal_ID() == 0)
				if(docType.get_ValueAsBoolean("isControlNoDocument")) {
					if (invoice.isSOTrx()) {
						String controlSequence = null;
						if (invoice.get_Value("LVE_controlNumber") == null) {
							if (docType.get_Value("LVE_ControlNoSequence_ID") == null) {
								throw new AdempiereException(msgSeqNotFound);
							}
	
							int controlNoSequence_ID = docType.get_ValueAsInt("LVE_ControlNoSequence_ID");
							System.out.println("Doctype ID =" + controlNoSequence_ID);
							MSequence seq = new MSequence(Env.getCtx(), controlNoSequence_ID, po.get_TrxName());
							controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), invoice);
	
							Query query = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName());
	
							while (query.setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), controlSequence).count() > 0) {
								seq.setCurrentNext(seq.getCurrentNext() + 1);
								seq.saveEx();
								controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), invoice);
							}
							invoice.set_ValueOfColumn("LVE_controlNumber", controlSequence);
							invoice.saveEx();
						} else {
							boolean existCN = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName()).setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), invoice.get_Value("LVE_controlNumber")).count() > 0;
							if (existCN) {
								throw new AdempiereException(msgExistCN);
							}
						}
					} else {
						if (invoice.get_Value("LVE_controlNumber") == null) {
							throw new AdempiereException(msgMandataryCN);
						} else if (invoice.get_Value("LVE_POInvoiceNo") == null) {
							throw new AdempiereException(msgMandataryINo);
						} else {
							boolean existCN = new Query(Env.getCtx(), MInvoice.Table_Name, where + "AND LVE_controlNumber=? AND LVE_POInvoiceNo=?", po.get_TrxName()).setParameters(invoice.getAD_Org_ID(), invoice.getC_BPartner_ID(), invoice.get_ID(), invoice.isSOTrx(), invoice.get_Value("LVE_controlNumber"), invoice.get_Value("LVE_POInvoiceNo")).count() > 0;
							if (existCN) {
								throw new AdempiereException(msgExistCN);
							}
						}
					}
				}
		}
		else if (po.get_TableName().equals(I_M_Movement.Table_Name) && type.equals(IEventTopics.DOC_BEFORE_COMPLETE)) {
			String msgExistCN = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgSeqNotFound = Msg.translate(Env.getCtx(), "SequenceDocNotFound") + " " + Msg.getElement(Env.getCtx(), "LVE_ControlNoSequence_ID");

			String where = "AD_Org_ID=? AND C_BPartner_ID=? AND M_Movement_ID!=? AND DocStatus IN ('CO','CL') ";
			MMovement move = (MMovement) po;
			MDocType docType = (MDocType) move.getC_DocType();

			if (move.getReversal_ID() == 0)
				if(docType.get_ValueAsBoolean("isControlNoDocument")) {
					String controlSequence = null;
					if (move.get_Value("LVE_controlNumber") == null) {
						if (docType.get_Value("LVE_ControlNoSequence_ID") == null) {
							throw new AdempiereException(msgSeqNotFound);
						}

						int controlNoSequence_ID = docType.get_ValueAsInt("LVE_ControlNoSequence_ID");
						MSequence seq = new MSequence(Env.getCtx(), controlNoSequence_ID, po.get_TrxName());
						controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), move);

						Query query = new Query(Env.getCtx(), MMovement.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName());

						while (query.setParameters(move.getAD_Org_ID(), move.getC_BPartner_ID(), move.get_ID(), controlSequence).count() > 0) {
							seq.setCurrentNext(seq.getCurrentNext() + 1);
							seq.saveEx();
							controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), move);
						}
						move.set_ValueOfColumn("LVE_controlNumber", controlSequence);
						move.saveEx();
					} else {
						boolean existCN = new Query(Env.getCtx(), MMovement.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName()).setParameters(move.getAD_Org_ID(), move.getC_BPartner_ID(), move.get_ID(), move.get_Value("LVE_controlNumber")).count() > 0;
						if (existCN) {
							throw new AdempiereException(msgExistCN);
						}
					}
				}
		}
		else if (po.get_TableName().equals(I_M_InOut.Table_Name) && type.equals(IEventTopics.DOC_BEFORE_COMPLETE)) {
			String msgExistCN = Msg.translate(Env.getCtx(), "AlreadyExists") + ": " + Msg.getElement(Env.getCtx(), "LVE_controlNumber");
			String msgSeqNotFound = Msg.translate(Env.getCtx(), "SequenceDocNotFound") + " " + Msg.getElement(Env.getCtx(), "LVE_ControlNoSequence_ID");

			String where = "AD_Org_ID=? AND C_BPartner_ID=? AND M_InOut_ID!=? AND DocStatus IN ('CO','CL') ";
			MInOut io = (MInOut) po;
			MDocType docType = (MDocType) io.getC_DocType();

			if (io.getReversal_ID() == 0)
				if(docType.get_ValueAsBoolean("isControlNoDocument")) {
					String controlSequence = null;
					if (io.get_Value("LVE_controlNumber") == null) {
						if (docType.get_Value("LVE_ControlNoSequence_ID") == null) {
							throw new AdempiereException(msgSeqNotFound);
						}

						int controlNoSequence_ID = docType.get_ValueAsInt("LVE_ControlNoSequence_ID");
						MSequence seq = new MSequence(Env.getCtx(), controlNoSequence_ID, po.get_TrxName());
						controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), io);

						Query query = new Query(Env.getCtx(), MInOut.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName());

						while (query.setParameters(io.getAD_Org_ID(), io.getC_BPartner_ID(), io.get_ID(), controlSequence).count() > 0) {
							seq.setCurrentNext(seq.getCurrentNext() + 1);
							seq.saveEx();
							controlSequence = MSequence.getDocumentNoFromSeq(seq, po.get_TrxName(), io);
						}
						io.set_ValueOfColumn("LVE_controlNumber", controlSequence);
						io.saveEx();
					} else {
						boolean existCN = new Query(Env.getCtx(), MInOut.Table_Name, where + "AND LVE_controlNumber=?", po.get_TrxName()).setParameters(io.getAD_Org_ID(), io.getC_BPartner_ID(), io.get_ID(), io.get_Value("LVE_controlNumber")).count() > 0;
						if (existCN) {
							throw new AdempiereException(msgExistCN);
						}
					}
				}
		}*/
	}

	private void ValidateDeclarationDocument(MInvoice invoice) {
		MDocType dt = (MDocType) invoice.getC_DocType();
		
	}
	
	private boolean validateWithholdingNo(MLVEVoucherWithholding voucher) {
		
		MLCOWithholdingType withHoldingType = new MLCOWithholdingType(voucher.getCtx(), voucher.getLCO_WithholdingType_ID(), voucher.get_TrxName());	
		PreparedStatement pst = null;
		boolean isValidate = false;
		String sql  = "";
		if(withHoldingType.isSOTrx())
			sql = "Select LVE_VoucherWithholding_ID from LVE_VoucherWithholding where  withholdingno= '"+voucher.getWithholdingNo()+"' AND LCO_WithholdingType_ID="+voucher.getLCO_WithholdingType_ID()+" AND C_Bpartner_ID ="+voucher.getC_BPartner_ID()+" AND LVE_VoucherWithholding_ID != "+voucher.getLVE_VoucherWithholding_ID()+" AND docstatus = 'CO'";

		else
			sql = "Select LVE_VoucherWithholding_ID from LVE_VoucherWithholding where  withholdingno= '"+voucher.getWithholdingNo()+"' AND LCO_WithholdingType_ID="+voucher.getLCO_WithholdingType_ID()+" AND LVE_VoucherWithholding_ID != "+voucher.getLVE_VoucherWithholding_ID()+" AND docstatus = 'CO' AND AD_Org_ID = "+voucher.getAD_Org_ID();	
		
		try {
			 pst = DB.prepareStatement(sql, null);
			ResultSet rs = pst.executeQuery();
			if (!rs.next()) {
				isValidate = true;
			}
			else{ 
				String msj = "El nro de retención "+voucher.getWithholdingNo()+ " ya existe para este tipo de retención"; 
				if(withHoldingType.isSOTrx())
					msj = msj + " y este tercero";
			    throw new RuntimeException(msj); 
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
    	finally{
    		DB.close(pst);
    		pst = null;
    	}	

		return isValidate;
	}
	/*	TRANSFER CODE TO net.frontuari.lvedocumentcontrol BY JORGE COLMENAREZ
	public BigDecimal getOpenAmt (MInvoice invoice)
	{
		BigDecimal m_openAmt = Env.ZERO;
		
		if (invoice.isPaid())
			return Env.ZERO;
		
		m_openAmt = invoice.getGrandTotal();
		
		BigDecimal allocated = invoice.getAllocatedAmt();
		if (allocated != null)
		{
			m_openAmt = m_openAmt.add(allocated);
		}
		//
		if (invoice.isCreditMemo())
			return m_openAmt.negate();
		return m_openAmt;
	}	//	getOpenAmt
	*/
	/**
	 * Automatic Allocation between Credit/Debit Notes and Document Affected
	 * @author Jorge Colmenarez <mailto:jcolmenarez@frontuari.net>, 2020-04-30 09:30
	 * @param m_Invoice
	 */
	/*
	private void AutomaticAllocation(MInvoice m_Invoice)
	{
		StringBuffer whereClause = new StringBuffer();
		StringBuffer whereParam = new StringBuffer();
		List<Object> parameters	= new ArrayList<Object>();
		MInvoiceLine[] list  = null;
		m_Current_Alloc = null;
			
		whereClause.append(" AND LVE_invoiceAffected_ID IS NOT NULL");
		BigDecimal m_InvoiceApplyAmt = Env.ZERO;
		
		whereParam.append("C_Invoice_ID=? ");
		parameters.add(m_Invoice.get_ID());
		
		list = getInvoiceLines(m_Invoice,whereClause.toString());
		BigDecimal invoiceAffectedNewOpenAmt = Env.ZERO;
		BigDecimal lineAmount = Env.ZERO;
		for (MInvoiceLine mInvoiceLine : list) {
			int invoiceID = mInvoiceLine.get_ValueAsInt("LVE_invoiceAffected_ID");
			MInvoice invoiceAffected = MInvoice.get(m_Invoice.getCtx(), invoiceID);
			lineAmount = mInvoiceLine.getLineTotalAmt();
			
			BigDecimal invoiceAffectedOpenAmt = getOpenAmt(invoiceAffected);
			
			if(m_Invoice.isCreditMemo() && lineAmount.compareTo(invoiceAffectedOpenAmt) > 0)
				lineAmount = invoiceAffectedOpenAmt;
			
			//	Credit Notes
			if(m_Invoice.isCreditMemo())
				invoiceAffectedNewOpenAmt =  invoiceAffectedOpenAmt.subtract(lineAmount);
			else	//	Debit Notes
				invoiceAffectedNewOpenAmt =  invoiceAffectedOpenAmt.add(lineAmount);
			
			
			if(invoiceAffectedNewOpenAmt.compareTo(Env.ZERO) < 0)
				continue;
			
			addAllocation(m_Invoice.getC_BPartner_ID(), lineAmount, invoiceAffectedNewOpenAmt, m_Invoice, invoiceAffected.getC_Invoice_ID());
			m_InvoiceApplyAmt = m_InvoiceApplyAmt.add(lineAmount.negate());
		}
		//	Complete Allocation
		try {
			completeAllocation(m_Invoice, m_InvoiceApplyAmt);
		} catch (Exception e) {
			log.warning(e.getMessage());
			m_Current_Alloc.deleteEx(true);
		} finally {
			whereClause = new StringBuffer();
			whereParam = new StringBuffer();
			parameters	= new ArrayList<Object>();;
			list  = null;
			m_Current_Alloc = null;
		}
	}
	*/
	/**
	 * Complete Allocation
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 10/12/2014, 17:23:23
	 * @param m_Invoice 
	 * @param m_AmtAllocated 
	 * @return void
	 */
	/*
	private void completeAllocation(MInvoice m_Invoice, BigDecimal m_PayAmt) throws Exception{
		if(m_Current_Alloc != null){
			if(m_Current_Alloc.getDocStatus().equals(DocumentEngine.STATUS_Drafted)){
				BigDecimal amt = m_Invoice.getOpenAmt();
				
				MAllocationLine aLine = null;
				if(!m_Invoice.isCreditMemo()) {
					amt = amt.subtract(m_PayAmt.negate());
					aLine = new MAllocationLine (m_Current_Alloc, m_PayAmt, Env.ZERO, Env.ZERO, amt);
				}  else {
					amt = amt.subtract(m_PayAmt);
					aLine = new MAllocationLine (m_Current_Alloc, m_PayAmt.negate(),Env.ZERO, Env.ZERO, amt);
				}
				//
				aLine.setDocInfo(m_Current_C_BPartner_ID, 0, m_Invoice.getC_Invoice_ID());
				aLine.saveEx();
				//	
				if(m_Current_Alloc.getDocStatus().equals(DocumentEngine.STATUS_Drafted)){
					log.fine("Current Allocation = " + m_Current_Alloc.getDocumentNo());
					m_Current_Alloc.setDocAction(DocumentEngine.ACTION_Complete);
					m_Current_Alloc.processIt(DocumentEngine.ACTION_Complete);
					m_Current_Alloc.saveEx();			
				}	
			}
			m_Current_Alloc = null;
			m_Current_C_BPartner_ID = -1;
		}
	}
	*/

	/**
	 * Add Document Allocation
	 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a> 10/12/2014, 17:23:45
	 * @param p_C_BPartner_ID
	 * @param amtDocument
	 * @param openAmt
	 * @param m_Invoice
	 * @param p_C_Invoice_ID
	 * @return void
	 */
	/*
	private void addAllocation(int p_C_BPartner_ID, BigDecimal openAmt,
			BigDecimal payAmt, MInvoice m_Invoice, int p_C_Invoice_ID) {
		if(m_Current_C_BPartner_ID != p_C_BPartner_ID){
			
			MDocType dtAll = (MDocType) m_Invoice.getC_DocType();
			
			m_Current_Alloc = new MAllocationHdr(Env.getCtx(), false,	//	automatic
					Env.getContextAsDate(m_Invoice.getCtx(), "#Date"), m_Invoice.getC_Currency_ID(), Env.getContext(Env.getCtx(), "#AD_User_Name")+" "+Msg.translate(Env.getAD_Language(Env.getCtx()), "IsAutoAllocation"), m_Invoice.get_TrxName());
			m_Current_Alloc.setAD_Org_ID(m_Invoice.getAD_Org_ID());
			m_Current_Alloc.setC_DocType_ID(dtAll.get_ValueAsInt("C_DocTypeAllocation_ID"));
			m_Current_Alloc.saveEx();
		}
		//	
		
		if(m_Invoice.isCreditMemo())
			openAmt = openAmt.negate();
		
		MAllocationLine aLine = new MAllocationLine (m_Current_Alloc, openAmt, 
				Env.ZERO, Env.ZERO, payAmt);
		aLine.setDocInfo(p_C_BPartner_ID, 0, p_C_Invoice_ID);
		aLine.saveEx();
		//
		m_Current_C_BPartner_ID = p_C_BPartner_ID;
	}
	*/
	/**
	 * 	Get Invoice Lines of Invoice
	 * 	@param whereClause starting with AND
	 * 	@return lines
	 */
	/*
	public MInvoiceLine[] getInvoiceLines (MInvoice mInvoice, String whereClause)
	{
		String whereClauseFinal = "C_Invoice_ID=? ";
		if (whereClause != null)
			whereClauseFinal += whereClause;
		List<MInvoiceLine> list = new Query(Env.getCtx(), I_C_InvoiceLine.Table_Name, whereClauseFinal, mInvoice.get_TrxName())
										.setParameters(mInvoice.getC_Invoice_ID())
										.setOrderBy(I_C_InvoiceLine.COLUMNNAME_Line)
										.list();
		return list.toArray(new MInvoiceLine[list.size()]);
	}	//	getLines
	*/
}