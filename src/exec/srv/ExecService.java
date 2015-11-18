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
import exec.proto.SmsObjectC104;
import exec.proto.SmsObjectC105;
import exec.proto.SmsObjectS100;
import exec.proto.SmsObjectS101;
import exec.proto.SmsObjectS102;
import exec.proto.SmsObjectS103;
import exec.proto.SmsObjectS105;

public class ExecService {
	
	private final static Logger log = LoggerFactory.getLogger(ExecService.class);
	public final static int os_win = 1;
	public final static int os_unix = 2;
	
	private static ExecService instance = null;
	private HashMap<String, ExecUser> userMap = new HashMap<String, ExecUser>();
	private List<Command> cmdList = new ArrayList<Command>();
	
	private HashMap<String, ExecUser> onlineMap = new HashMap<String, ExecUser>();
	private HashMap<String, IoSession> onlineSession = new HashMap<String, IoSession>();
	private HashMap<String, ExecCmdThread> threadMap = new HashMap<String, ExecCmdThread>();
	private String version = "test";
	private int port = 0;
	private int os = os_unix;
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
	
	public int getOs() {
		return os;
	}
	
	public void removeThread(String key) {
		threadMap.remove(key);
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
			    	if (element.getNodeName().equals("version")) {
			    		version = element.getAttribute("value").trim();
			    	}
			    	if (element.getNodeName().equals("os")) {
			    		String osstr = element.getAttribute("value").trim();
			    		if ("win".equalsIgnoreCase(osstr)) {
			    			os = os_win;
			    		} else {
			    			os = os_unix;
			    		}
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
			    				if ("true".equals(userEle.getAttribute("admin"))) {
			    					user.setAdmin(true);
			    				}
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
			    				command.setUsername(cmdEle.getAttribute("username").trim());
			    				command.setDesc(cmdEle.getAttribute("desc").trim());
			    				command.setDir(cmdEle.getAttribute("dir").trim());
			    				command.setCmd(cmdEle.getTextContent().trim());
			    				String oneself = cmdEle.getAttribute("oneself");
			    				if ("true".equals(oneself)) {
			    					command.setOneself(true);
			    				}
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
		ExecUser user = (ExecUser)session.getAttribute("execUser");
		if (user != null) {
			ExecCmdThread running = threadMap.get(user.getName());
			if (running != null) {
				running.doStop();
			}
			threadMap.remove(user.getName());
			onlineMap.remove(user.getName());
			onlineSession.remove(user.getName());
		}
	}

	public ISmsObject handle(SmsObjectC100 obj) {
		ExecUser user = userMap.get(obj.getName());
		SmsObjectS100 s100 = new SmsObjectS100();
		if (!version.equals(obj.getVersion())) {
			s100.setSucc(0);
			s100.setMsg("版本不一致，请更新版本!");
			obj.getSession().write(s100);
			log.info(user.getName() + "登录失败[版本不一致]");
			return null;
		}
		if (onlineMap.get(obj.getName()) != null) {
			s100.setSucc(0);
			s100.setMsg("你已经登录，不可以重复登录!");
			log.error("登录失败:" + obj.getName() 
					+ " onlineMap size:" +  onlineMap.size() 
					+ " onlineSession size:" + onlineSession.size());
			obj.getSession().write(s100);
			log.info(user.getName() + "登录失败[重复登录]");
			if (onlineSession.get(obj.getName()) == null) {
				onlineMap.remove(obj.getName());
			} else {
				IoSession s = onlineSession.get(obj.getName());
				if (!s.isConnected() || s.isClosing()) {
					sessionClosed(s);
				} else {
					SmsObjectS102 sms102 = new SmsObjectS102();
					sms102.setLine("测试信息");
		        	s.write(sms102);
				}
			}
			return null;
		}
		if (user != null && user.getSignature().equals(obj.getSignature())) {
			obj.getSession().setAttribute("verification", true);
			obj.getSession().setAttribute("execUser", user);
			s100.setSucc(1);
			s100.setMsg("登录成功");
			if (isAdmin(user)) {
				s100.setGroup(9);
			} else {
				s100.setGroup(1);
			}
			obj.getSession().write(s100);
			SmsObjectS101 s101 = new SmsObjectS101();
			s101.setCmdList(getCommandByUser(user));
			obj.getSession().write(s101);
			onlineMap.put(user.getName(), user);
			onlineSession.put(user.getName(), obj.getSession());
			log.info(user.getName() + "登录成功[" + obj.getSession().getId() + "]");
		} else {
			s100.setSucc(0);
			s100.setMsg("验收失败，你可能没有权限!");
			obj.getSession().write(s100);
			log.info(user.getName() + "登录失败[验收失败]");
		}
		return null;
	}
	
	public ISmsObject handle(SmsObjectC102 sms) {
		ExecUser user = (ExecUser)sms.getSession().getAttribute("execUser");
		List<String> list = sms.getCmdList();
		String cmds = "";
		for (String cmd : list) {
			cmds += " [" + cmd + "]";
		}
		if (user != null) {
			log.info(user.getName() + "执行命令:" + cmds);
			if (threadMap.get(user.getName()) == null) {
				String oneselfMsg = checkOneself(sms);
				if (oneselfMsg == null) {
					ExecCmdThread thread = new ExecCmdThread(user, sms);
					thread.start();
					threadMap.put(user.getName(), thread);
				} else {
					SmsObjectS103 sms103 = new SmsObjectS103();
					sms103.setLine("存在独占任务，请稍后再试:" + oneselfMsg);
					sms.getSession().write(sms103);
				}
			} else {
				SmsObjectS103 sms103 = new SmsObjectS103();
				sms103.setLine("你已经有任务在执行了");
				sms.getSession().write(sms103);
			}
		} else {
			SmsObjectS103 sms103 = new SmsObjectS103();
			sms103.setLine("账号信息异常，请重新登录");
			sms.getSession().write(sms103);
		}
		return null;
	}
	
	public ISmsObject handle(SmsObjectC104 sms) {
		ExecUser user = (ExecUser)sms.getSession().getAttribute("execUser");
		log.info(user.getName() + "执行命令:" + sms.getCmdkey());
		if (user != null) {
			if (threadMap.get(user.getName()) == null) {
				String oneselfMsg = checkOneself(sms);
				if (oneselfMsg == null) {
					ExecCmdThread thread = new ExecCmdThread(user, sms);
					thread.start();
					threadMap.put(user.getName(), thread);
				} else {
					SmsObjectS103 sms103 = new SmsObjectS103();
					sms103.setLine("存在独占任务，请稍后再试:" + oneselfMsg);
					sms.getSession().write(sms103);
				}
			} else {
				SmsObjectS103 sms103 = new SmsObjectS103();
				sms103.setLine("你已经有任务在执行了");
				sms.getSession().write(sms103);
			}
		} else {
			SmsObjectS103 sms103 = new SmsObjectS103();
			sms103.setLine("账号信息异常，请重新登录");
			sms.getSession().write(sms103);
		}
		return null;
	}
	
	public ISmsObject handle(SmsObjectC105 sms) {
		ExecUser user = (ExecUser)sms.getSession().getAttribute("execUser");
		if (isAdmin(user)) {
			initConfig();
		}
		SmsObjectS105 sms105 = new SmsObjectS105();
		sms105.setSucc(1);
		sms.getSession().write(sms105);
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
	
	public List<Command> getCommandByUser(ExecUser user) {
		String group = user.getGroup();
		String username = user.getName();
		List<Command> list = new ArrayList<Command>();
		for (Command cmd : cmdList) {
			if (cmd.getGroups().indexOf(group) != -1 || cmd.getUsername().indexOf(username) != -1) {
				list.add(cmd);
			}
		}
		return list;
	}
	
	public String checkOneself(SmsObjectC102 sms) {
		List<String> cmdList = sms.getCmdList();
		for (ExecCmdThread thread : threadMap.values()) {
			for (String cmd : cmdList) {
				if (thread.getOneselfMap().get(cmd) != null)
					return "cmd:" + cmd + " user:"+ thread.getOneselfMap().get(cmd);
			}
		}
		return null;
	}
	
	public String checkOneself(SmsObjectC104 sms) {
		for (ExecCmdThread thread : threadMap.values()) {
			if (thread.getOneselfMap().get(sms.getCmdkey()) != null)
				return "cmd:" + sms.getCmdkey() + " user:"+ thread.getOneselfMap().get(sms.getCmdkey());
		}
		return null;
	}
	
	public boolean isAdmin(ExecUser user) {
		if (user == null)
			return false;
		return user.isAdmin();
	}
}
