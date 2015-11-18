package exec.srv;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecCloseThread extends Thread {
	
	private final static Logger log = LoggerFactory.getLogger(ExecCloseThread.class);
	
	public void run() {
		try {
			System.in.read();
			String st = "";
			BufferedReader buf = new BufferedReader(new InputStreamReader(System.in));
			st = buf.readLine();
			do {
				log.info("Press [yes] can close server!!!");
				buf = new BufferedReader(new InputStreamReader(System.in));
				st = buf.readLine();
			} while(!"yes".equals(st));
		} catch (Exception e) {
			e.printStackTrace();
		}
		System.exit(0);
	}
}
