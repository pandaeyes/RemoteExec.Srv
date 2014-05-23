package exec.srv;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.mina.core.session.IoSession;

import exec.common.Command;
import exec.common.ISmsObject;
import exec.proto.SmsObjectC102;
import exec.proto.SmsObjectC104;
import exec.proto.SmsObjectS102;
import exec.proto.SmsObjectS104;

public class ExecCmdThread extends Thread {
	
	private ExecUser user = null;
	private ISmsObject isms = null;
	private HashMap<String, String> onselfMap = new HashMap<String, String>();
	
	public ExecCmdThread(ExecUser user, ISmsObject sms) {
		this.isms = sms;
		this.user = user;
	}
	
	public void run() {
		try {
			String fileName = "";
			String charsetName = "UTF-8";
			Process proc = null;
			if (ExecService.getInstance().getOs() == ExecService.os_unix) {
				fileName = "_remote_script_" + user.getName() + "_tmp.sh";
				charsetName = "UTF-8";
				makeScript(fileName);
				proc = Runtime.getRuntime().exec("sh " + fileName);
			} else {
				fileName = "_remote_script_" + user.getName() + "_tmp.bat";
				charsetName = "GB2312";
				makeScript(fileName);
				proc = Runtime.getRuntime().exec("cmd /c " + fileName);
			}
			InputStream isinfo = proc.getInputStream();
			InputStream iserr = proc.getErrorStream();
	        InputStreamReader isr = new InputStreamReader(isinfo, charsetName);
	        BufferedReader br = new BufferedReader(isr);
	        
	        InputStreamReader isrerr = new InputStreamReader(iserr, charsetName);
	        BufferedReader brerr = new BufferedReader(isrerr);
	        String line = null; 
	        SmsObjectS102 sms = null;
	        IoSession session = isms.getSession();
	        while ((line = br.readLine()) != null){
	        	sms = new SmsObjectS102();
	        	sms.setLine(line);
	        	session.write(sms);
	        	System.out.println(line);
	        }
	        int exitVal = proc.waitFor();
	        while ((line = brerr.readLine()) != null){
	        	exitVal = 1;
	        	sms = new SmsObjectS102();
	        	sms.setLine("ERROR:" + line);
	        	session.write(sms);
	        	System.out.println(line);
	        }
	        SmsObjectS104 sms104 = new SmsObjectS104();
	        if(exitVal == 0){
	        	sms104.setResult(1);
	        	sms = new SmsObjectS102();
	        	sms.setLine("执行成功");
	        	session.write(sms);
	        	System.out.println("执行成功");
	        }else{
	        	sms104.setResult(0);
	        	sms = new SmsObjectS102();
	        	sms.setLine("执行失败");
	        	session.write(sms);
	        	System.out.println("执行失败");
	        } 
	        session.write(sms104);
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			ExecService.getInstance().removeThread(user.getName());
		}
	}
	
	public HashMap<String, String> getOneselfMap() {
		return onselfMap;
	}
	
	private void makeScript(String fileName) {
		try {
			switch (ExecService.getInstance().getOs()) {
				case ExecService.os_win :
					makeWinScript(fileName);
					break;
				default:
					makeUnixScript(fileName);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	private void makeWinScript(String fileName) throws IOException {
		File file = new File(fileName);
		OutputStreamWriter write = new OutputStreamWriter(new FileOutputStream(file),"GB2312");
		BufferedWriter confBf =new BufferedWriter(write);
		confBf.write("@echo off\r\n");
		confBf.write("echo 正在执行\r\n");
		if (isms instanceof SmsObjectC102) {
			SmsObjectC102 sms102 = (SmsObjectC102)isms;
			List<String> keyList = sms102.getCmdList();
			List<Command> cmdList = ExecService.getInstance().getCommandByUser(user);
			for (String key : keyList) {
				Command cmd = getCommandByKey(cmdList, key);
				if (cmd != null) {
					if (cmd.isOneself()) {
						onselfMap.put(cmd.getCmd(), user.getName());
					}
					confBf.write("\r\n");
					if (cmd.getDir().trim().length() > 0)
						confBf.write("cd /d " + cmd.getDir() + "\r\n");
					confBf.write(cmd.getCmd() + "\r\n");
				}
			}
		} else if(isms instanceof SmsObjectC104) {
			SmsObjectC104 sms104 = (SmsObjectC104)isms;
			String key = sms104.getCmdkey();
			List<Command> cmdList = ExecService.getInstance().getCommandByUser(user);
			Command cmd = getCommandByKey(cmdList, key);
			String param = sms104.getParam();
			if (cmd != null && param.matches("(\\w*_*)*")) {
				if (cmd.isOneself()) {
					onselfMap.put(cmd.getCmd(), user.getName());
				}
				confBf.write("\r\n");
				if (cmd.getDir().trim().length() > 0)
					confBf.write("cd /d " + cmd.getDir() + "\r\n");
				confBf.write(replaceParam(cmd.getCmd(), param) + "\r\n");
			}
		}
		confBf.write("echo finish\n");
		confBf.close();
		write.close();
	}
	
	private void makeUnixScript(String fileName) throws IOException {
		File file = new File(fileName);
		OutputStreamWriter write = new OutputStreamWriter(new FileOutputStream(file),"UTF-8");
		BufferedWriter confBf =new BufferedWriter(write);
		confBf.write("#/bin/bash\n");
		confBf.write("echo 正在执行:\n");
		if (isms instanceof SmsObjectC102) {
			SmsObjectC102 sms102 = (SmsObjectC102)isms;
			List<String> keyList = sms102.getCmdList();
			List<Command> cmdList = ExecService.getInstance().getCommandByUser(user);
			for (String key : keyList) {
				Command cmd = getCommandByKey(cmdList, key);
				if (cmd != null) {
					if (cmd.isOneself()) {
						onselfMap.put(cmd.getCmd(), user.getName());
					}
					confBf.write("\n");
					if (cmd.getDir().trim().length() > 0)
						confBf.write("cd " + cmd.getDir() + "\n");
					confBf.write(cmd.getCmd() + "\n");
				}
			}
		} else if(isms instanceof SmsObjectC104) {
			SmsObjectC104 sms104 = (SmsObjectC104)isms;
			String key = sms104.getCmdkey();
			List<Command> cmdList = ExecService.getInstance().getCommandByUser(user);
			Command cmd = getCommandByKey(cmdList, key);
			String param = sms104.getParam();
			if (cmd != null && param.matches("(\\w*_*)*")) {
				if (cmd.isOneself()) {
					onselfMap.put(cmd.getCmd(), user.getName());
				}
				confBf.write("\n");
				if (cmd.getDir().trim().length() > 0)
					confBf.write("cd " + cmd.getDir() + "\n");
				confBf.write(replaceParam(cmd.getCmd(), param) + "\n");
			}
		}
		confBf.write("echo finish\n");
		confBf.close();
		write.close();
	}
	
	private String replaceParam(String cmd, String param) {
		if (param.trim().length() == 0)
			return cmd;
		String r = cmd.replaceFirst("\\$\\{param\\}", param);
		return r;
	}
	
	private Command getCommandByKey(List<Command> cmdList, String key) {
		for (Command cmd : cmdList) {
			if (cmd.getKey().equals(key)) {
				return cmd;
			}
		}
		return null;
	}
}
