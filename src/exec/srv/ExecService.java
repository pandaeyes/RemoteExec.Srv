package exec.srv;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.mina.core.session.IoSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import exec.common.Command;
import exec.common.ISmsObject;
import exec.proto.SmsObjectC100;
import exec.proto.SmsObjectC102;
import exec.proto.SmsObjectS100;
import exec.proto.SmsObjectS101;

public class ExecService {
	
	private final static Logger log = LoggerFactory.getLogger(ExecService.class);
	
	private static ExecService instance = null;
	private HashMap<String, ExecUser> userMap = new HashMap<String, ExecUser>();
	private List<Command> cmdList = new ArrayList<Command>();
	private int port = 0;
	private Map<Class, Method> methodList = new HashMap<Class, Method>();
	
	private ExecService(){
		Method [] Methods = this.getClass().getMethods();
		for (Method m : Methods) {
			if ((m.getModifiers() & Modifier.PUBLIC) != 0 && "handle".equals(m.getName())) {
				if (!m.getParameterTypes()[0].isInterface())
					methodList.put(m.getParameterTypes()[0], m);
			}
		}
		initConfig();
	}
	public static ExecService getInstance() {
		if (instance == null) {
			instance = new ExecService();
		}
		return instance;
	}
	
	public int getPort() {
		return port;
	}
	
	private void initConfig() {
		userMap.clear();
		cmdList.clear();
		File file = new File("config.xml");
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder; 
		Document document = null; 
		try {
			builder = builderFactory.newDocumentBuilder();
		    document = builder.parse(file);
		    Element root = document.getDocumentElement();
		    NodeList nodeList = root.getChildNodes();
		    int size = nodeList.getLength();
		    for (int i = 0; i < size; i++) {
		    	Node node = nodeList.item(i);
		    	if (node instanceof Element) {
		    		Element element = (Element)node;
			    	if (element.getNodeName().equals("port")) {
			    		port = Integer.parseInt(element.getAttribute("value").trim());
			    	}
			    	if (element.getNodeName().equals("users")) {
			    		NodeList userList = element.getChildNodes();
			    		int usize = userList.getLength();
			    		Element userEle = null;
			    		Node userNode = null;
			    		for (int j = 0; j < usize; j++) {
			    			userNode = userList.item(j);
			    			if (userNode instanceof Element && ((Element)userNode).getNodeName().equals("user")) {
			    				ExecUser user = new ExecUser();
			    				userEle = (Element)userNode;
			    				user.setGroup(userEle.getAttribute("group").trim());
			    				user.setName(userEle.getAttribute("name").trim());
			    				user.setSignature(userEle.getAttribute("signature").trim());
			    				userMap.put(user.getName(), user);
			    			}
			    		}
			    	}
			    	if (element.getNodeName().equals("commands")) {
			    		NodeList cmdNodeList = element.getChildNodes();
			    		int csize = cmdNodeList.getLength();
			    		Element cmdEle = null;
			    		Node cmdNode = null;
			    		for (int k = 0; k < csize; k++) {
			    			cmdNode = cmdNodeList.item(k);
			    			if (cmdNode instanceof Element && ((Element)cmdNode).getNodeName().equals("cmd")) {
			    				Command command = new Command();
			    				cmdEle = (Element)cmdNode;
			    				command.setKey(cmdEle.getAttribute("key").trim());
			    				command.setGroups(cmdEle.getAttribute("groups").trim());
			    				command.setDesc(cmdEle.getAttribute("desc").trim());
			    				command.setDir(cmdEle.getAttribute("dir").trim());
			    				command.setCmd(cmdEle.getTextContent().trim());
			    				cmdList.add(command);
			    			}
			    		}
			    	}
		    	}
		    }
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void sessionClosed(IoSession session) {
	}

	public ISmsObject handle(SmsObjectC100 obj) {
		ExecUser user = userMap.get(obj.getName());
		SmsObjectS100 s100 = new SmsObjectS100();
		if (user != null && user.getSignature().equals(obj.getSignature())) {
			obj.getSession().setAttribute("verification", true);
			obj.getSession().setAttribute("execUser", user);
			s100.setSucc(1);
			obj.getSession().write(s100);
			SmsObjectS101 s101 = new SmsObjectS101();
			s101.setCmdList(getCommandByUser(user));
			obj.getSession().write(s101);
		} else {
			s100.setSucc(0);
			obj.getSession().write(s100);
		}
		return null;
	}
	
	public ISmsObject handle(SmsObjectC102 sms) {
		System.out.println("handle 102");
		List<String> list = sms.getCmdList();
		for (String cmd : list) {
			System.out.println("handle 102:" + cmd);
		}
		return null;
	}
	
	public ISmsObject handle(ISmsObject obj) {
		Class c = obj.getClass();
		Method method = methodList.get(c);
		if (method == null) {
			log.error("没有协议的处理方法:" + obj.getProto());
			return null;
		} else {
			Object returnSms = null;
			try {
				returnSms = method.invoke(this, new Object[]{obj});
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
			return (ISmsObject)returnSms;
		}
	}
	
	private List<Command> getCommandByUser(ExecUser user) {
		String group = user.getGroup();
		List<Command> list = new ArrayList<Command>();
		for (Command cmd : cmdList) {
			if (cmd.getGroups().indexOf(group) != -1) {
				list.add(cmd);
			}
		}
		return list;
	}
}
