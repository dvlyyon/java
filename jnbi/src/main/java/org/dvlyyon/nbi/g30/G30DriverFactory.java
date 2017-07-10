package org.dvlyyon.nbi.g30;

import java.io.InputStream;
import java.util.Vector;

import org.dvlyyon.nbi.helper.DHelperObject;
import org.dvlyyon.nbi.CommonEngineImpl;
import org.dvlyyon.nbi.CommonFunctionImpl;
import org.dvlyyon.nbi.DObject;
import org.dvlyyon.nbi.DObjectManager;
import org.dvlyyon.nbi.DriverEngineInf;
import org.dvlyyon.nbi.DriverFactoryInf;
import org.dvlyyon.nbi.DriverFunction;
import org.dvlyyon.nbi.SNIMetadata;
import org.dvlyyon.nbi.model.DNetconfObjectModel;
import org.dvlyyon.nbi.model.DObjectAction;
import org.dvlyyon.nbi.model.DObjectModel;
import org.dvlyyon.nbi.model.DObjectType;
import org.dvlyyon.nbi.util.CommonUtils;
import org.dvlyyon.nbi.CliInterface;
import static org.dvlyyon.nbi.CommonMetadata.*;

public class G30DriverFactory implements DriverFactoryInf {
	
	static final String [] supportedIntfType = 
		{NBI_TYPE_CLI_SSH,
		 NBI_TYPE_NETCONF,
		 NBI_TYPE_ODL,
		 NBI_TYPE_RESTCONF,
		 NBI_TYPE_SNMP};

	static final int SSH_INDEX      = 0;
	static final int NETCONF_INDEX  = 1;
	static final int ODL_INDEX      = 2;
	static final int RESTCONF_INDEX = 3;
	static final int SNMP_INDEX 	= 4;

	boolean isSupportedInterface(String intf) {
		for (String item:supportedIntfType) {
			if (intf.equalsIgnoreCase(item))
				return true;
		}
		return false;
	}
	
	boolean isSSHCLIInterface(String intf) {
		if (intf.equalsIgnoreCase(supportedIntfType[SSH_INDEX]))
			return true;
		return false;
	}

	boolean isNetconfInterface(String intf) {
		if (intf.equalsIgnoreCase(supportedIntfType[NETCONF_INDEX]))
			return true;
		return false;
	}

	boolean isODLInterface(String intf) {
		return intf.equalsIgnoreCase(supportedIntfType[ODL_INDEX]);
	}
	
	boolean isRestconfInterface(String intf) {
		return intf.equalsIgnoreCase(supportedIntfType[RESTCONF_INDEX]);
	}

	boolean isSnmpInterface(String intf) {
		return intf.equalsIgnoreCase(supportedIntfType[SNMP_INDEX]);
	}

	@Override
	public DriverEngineInf createEngine(String platformName, DObjectModel objModel) {
		DriverFunction function = new CommonFunctionImpl();
		return new CommonEngineImpl(platformName, objModel, function);		
	}

	
	@Override
	public CliInterface createCliSession(String intfType, DObjectModel objModel, String cmd,
			DObject ap, Vector<String> err) {
		String ret = null;
		CliInterface cli = null;
		if (ap.isNode() || ap.isSession()) {
			String debug = objModel.getProperty(OBJECT_MODEL_PROPERTY_SHOW_CLI_COMMAND_ONLY);
			if (SNIMetadata.isStubCmd(cmd) ||
					(debug!=null&&debug.trim().equalsIgnoreCase("yes"))) {
				cli = new G30CliStub();
				if (!isSupportedInterface(intfType)) {
					err.add(0,"The " + intfType + " is not supported by DCI platform");
					return null;					
				}
				if (isSSHCLIInterface(intfType)) {
					NBIAdapterInf cliAdapter = new G30CliImpl();
					((G30CliStub)cli).setAdapter(cliAdapter);
				} else if (isNetconfInterface(intfType)) {
					NBIAdapterInf cliAdapter = new G30NetconfImpl();
					((G30CliStub)cli).setAdapter(cliAdapter);
				} else if (isODLInterface(intfType)) {
					NBIAdapterInf cliAdapter = new G30OdlImpl();
					((G30CliStub)cli).setAdapter(cliAdapter);
				} else if (isRestconfInterface(intfType)) {
					NBIAdapterInf cliAdapter = new G30RestconfImpl();
					((G30CliStub)cli).setAdapter(cliAdapter);
				} else if (isSnmpInterface(intfType)) {
					NBIAdapterInf cliAdapter = new G30SnmpImpl();
					((G30CliStub)cli).setAdapter(cliAdapter);
				} else {
					err.add(0,"The " + intfType + " is not supported by DCI platform");
					return null;					
				}
				ret = cli.login(ap);
			} else {
				if (!isSupportedInterface(intfType)) {
					err.add(0,"The " + intfType + " is not supported by DCI platform");
					return null;					
				}
				if (isSSHCLIInterface(intfType)) {
					cli = new G30CliImpl(); 
					String separator = objModel.getProperty(OBJECT_MODEL_PROPERTY_PATTERN_SEPARATOR);
					if (separator!=null) cli.setPatternSeparator(separator);
					String endPattern = objModel.getProperty(OBJECT_MODEL_PROPERTY_ENDPATTERN);
					String errorPattern = objModel.getProperty(OBJECT_MODEL_PROPERTY_ERRORPATTERN);
					String loginErrorPattern = objModel.getProperty(OBJECT_MODEL_PROPERTY_LOGIN_ERROR_PATTERN);
					String rebootCmdPattern = objModel.getProperty(OBJECT_MODEL_PROPERTY_REBOOT_CMD_PATTERN);
					String cliPort = objModel.getProperty(OBJECT_MODEL_PROPERTY_CLI_PORT);
					String interactiveMode = objModel.getProperty(OBJECT_MODEL_PROPERTY_INTERACTIVE_MODE);

					if (endPattern==null || errorPattern==null || loginErrorPattern == null || rebootCmdPattern == null) {
						err.add(0,"endpattern, errorpattern and loginErrorPattern must be defined in object model");
						return null;
					}
					String sshImpl = objModel.getProperty(OBJECT_MODEL_PROPERTY_SSH_IMPLEMENTATION);
					if (sshImpl != null) ((G30CliImpl)cli).setSSHImpl(sshImpl);
					String connectionType = objModel.getProperty(OBJECT_MODEL_PROPERTY_CONNECTION_TYPE);
					if (connectionType == null) connectionType = "cli";
					((G30CliImpl)cli).setConnectionType(connectionType);
					String conInfoPattern = objModel.getProperty(OBJECT_MODEL_PROPERTY_CONNECTION_INFO_PATTERN);
					if (conInfoPattern != null) {
						((G30CliImpl)cli).setConnectionInfoPattern(conInfoPattern);
					}
					((G30CliImpl)cli).setLoginError(loginErrorPattern);
					((G30CliImpl)cli).setRebootCmd(rebootCmdPattern);
					((G30CliImpl)cli).setInteractiveMode(interactiveMode);
					if (cliPort != null) ((G30CliImpl)cli).setCliPort(cliPort);
					cli.setEndPatternList(endPattern);
					cli.setErrorPatternList(errorPattern);
					ret = cli.login(ap);
				} else if (isNetconfInterface(intfType)) {
					cli = new G30NetconfImpl();
					ret = cli.login(ap);
				} else if (isODLInterface(intfType)) {
					cli = new G30OdlImpl();
					ret = cli.login(ap);
				} else if (isRestconfInterface(intfType)) {
					cli = new G30RestconfImpl();
					ret = cli.login(ap);
				} else if (isSnmpInterface(intfType)) {
					cli = new G30SnmpImpl();
					ret = cli.login(ap);
				} else {
					err.add(0,"The " + intfType + " is not supported by DCI platform");
					return null;					
				}
			} 
		} else {
			err.add(0, "node "+ap.getID()+" does not support CLI");
			return null;
		}
		if (!ret.equals("OK")) {
			err.add(0, ret);
			return null;
		}
		return cli;
	}

	@Override
	public DObject getObjectInstance(DObjectManager manager, String name, String type,
			Vector<String> err) {
		DObjectType objType = manager.getObjectModel().getObjectType(type);
		if (objType == null) {
			err.add(0, "Cannot find Object Type of type("+name+")");
			return null;
		}
		DObject obj = null;
		String value = objType.getProperty(OBJECT_TYPE_ATTRIBUTE_IS_HELPER);
		if (CommonUtils.isConfirmed(value)) {
			obj = new DHelperObject();
		} else {
			obj = new DSnmpObject();
		}
		obj.setName(name);
		obj.setType(type);
		// TOBD set driver specific information
		return obj;
	}

	@Override
	public String getInterfaceType(DObjectModel objModel, DObject obj, String actionName) {
		if (actionName.startsWith(DRIVER_UNDO_ACTION_PRE)) 
			return NBI_TYPE_CLI_SSH;
		DObject node = obj.getAncester();
		DObjectType objType = objModel.getObjectType(obj.getType());
		DObjectAction actType = objType.getAction(actionName);
		String infType = node.getAttributeValue(NODE_CONTEXT_ATTRIBUTE_INTERFACE_TYPE);
		if (infType==null) infType = NBI_TYPE_CLI_SSH;
		
		String objInfType = objType.getProperty(OBJECT_TYPE_ATTRIBUTE_SUPPORT);
		String actInfType = actType.getProperty(OBJECT_TYPE_ATTRIBUTE_SUPPORT);
		
		String defaultSupporRule = objModel.getProperty(infType+"Default");
		String autoSwitch = node.getAttributeValue(NODE_CONTEXT_ATTRIBUTE_AUTO_SWITCH);
		if (CommonUtils.isConfirmed(autoSwitch)) {
			String supportedInfType = objType.getProperty(OBJECT_TYPE_ATTRIBUTE_SUPPORT);
			if (supportedInfType != null && !supportedInfType.equals(infType)) {//we support support missing or only one value
				infType = supportedInfType;
			} else if (supportedInfType == null) {
				supportedInfType = actType.getProperty(OBJECT_TYPE_ATTRIBUTE_SUPPORT);
				if (supportedInfType != null && !supportedInfType.equals(infType)) {
					infType = supportedInfType;
				}
			}
		}
		return infType;
	}
	
	@Override
	public String[] getSupportedInterfaceType() {
		// TODO Auto-generated method stub
		return this.supportedIntfType;
	}

	@Override
	public DObjectModel getObjectModel(InputStream file) throws Exception {
		DNetconfObjectModel objModel = new DNetconfObjectModel();
		String result = objModel.init(file);
		if (result != null)
			throw new Exception(result);
		return objModel;
	}

	@Override
	public String getAllInterfaceType(DObjectModel objModel, DObject obj,
			String actionName) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String infType:supportedIntfType) {
			if (!first) sb.append(META_ACTION_OUTPUT_FORMAT_SEPARATOR);
			sb.append(infType);
			first = false;
		}
		return sb.toString();
	}
}
