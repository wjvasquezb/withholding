package ve.net.dcs.callout;

import java.util.Properties;

import org.adempiere.base.IColumnCallout;
import org.compiere.model.GridField;
import org.compiere.model.GridTab;
import org.compiere.model.MSysConfig;
import org.globalqss.model.MLCOWithholdingType;

import ve.net.dcs.model.I_LVE_VoucherWithholding;

public class VWTSetDocumentNo implements IColumnCallout {

	@Override
	public String start(Properties ctx, int WindowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		if (mField.getColumnName().equals(I_LVE_VoucherWithholding.COLUMNNAME_WithholdingNo)){
			return getDocNo(ctx, WindowNo, mTab, mField, value, oldValue);
		}
		if (mField.getColumnName().equals(I_LVE_VoucherWithholding.COLUMNNAME_LCO_WithholdingType_ID)){
			return SetDocTypeID(ctx, WindowNo, mTab, mField, value, oldValue);
		}
		return null;
	}
	
	
	private String getDocNo(Properties ctx, int windowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		String DocumentNo= (String)value;
		if(DocumentNo.length()>0){
			mTab.setValue(I_LVE_VoucherWithholding.COLUMNNAME_DocumentNo, DocumentNo);
		}
		//	return 
		return null;
	}
	
	private String SetDocTypeID(Properties ctx, int windowNo, GridTab mTab,
			GridField mField, Object value, Object oldValue) {
		Integer WHtype= (Integer)value;
		if(WHtype.intValue()>0){
			MLCOWithholdingType WithholdingType = new MLCOWithholdingType(ctx, WHtype, null);  
			mTab.setValue(I_LVE_VoucherWithholding.COLUMNNAME_C_DocType_ID, WithholdingType.get_ValueAsInt("C_Doctype_ID"));
			}
		//	return 
		return null;
	}

}
