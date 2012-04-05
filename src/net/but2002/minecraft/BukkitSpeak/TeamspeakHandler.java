package net.but2002.minecraft.BukkitSpeak;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;

import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.ClientMovedEvent;
import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.ClientlistEvent;
import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.EnterEvent;
import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.ErrorEvent;
import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.LeaveEvent;
import net.but2002.minecraft.BukkitSpeak.teamspeakEvent.ServerMessageEvent;

public class TeamspeakHandler implements Runnable {
	
	BukkitSpeak plugin;
	StringManager stringManager;
	Boolean kill = false;
	Boolean isRunning = false;
	
	Date tsStarted;
	Date tsStopped;
	
	HashMap<Integer, TeamspeakUser> users = new HashMap<Integer, TeamspeakUser>();
	TeamspeakKeepAlive keepAliveThread;
	
	Socket socket;
	PrintWriter out;
	BufferedReader in;
	
	public TeamspeakHandler(BukkitSpeak plugin){
		this.plugin = plugin;
		stringManager = plugin.getStringManager();
		tsStarted = null;
		tsStopped = null;
		kill = false;
		isRunning = false;
	}
	
	@Override
	public void run() {
		try {
			Thread.sleep(2000);
			setAlive(true);
			connect();
			
			while (!kill) {
				if (socket.isClosed()) connect();
				
				String line = in.readLine();
				while (!line.isEmpty()) {
					handleMessage(line);
					Thread.sleep(50);
					line = in.readLine();
				}
				Thread.sleep(1000);
			}
			
			setAlive(false);
			try {
				out.println("logout");
				plugin.getLogger().info("Logged out properly.");
			} catch (Exception e) {
				plugin.getLogger().warning("Could not logout properly. Shouldn't be a problem though.");
			}
			in.close();
			out.close();
			socket.close();
			
		} catch (Exception e) {
			setAlive(false);
			plugin.getLogger().severe("Exception while listening to the Teamspeak Query.");
			e.printStackTrace();
		}
	}
	
	public void connect() {
		try {
			socket = new Socket(InetAddress.getByName(stringManager.getIp()), stringManager.getQueryPort());
			out = new PrintWriter(socket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			
			out.println("login " + stringManager.getServerAdmin() + " " + stringManager.getServerPass());
			out.println("use port=" + stringManager.getServerPort());
			out.println("clientupdate client_nickname=" + stringManager.getTeamspeakNickname());
			out.println("servernotifyregister event=server");
			if (stringManager.getUseTextServer()) out.println("servernotifyregister event=textserver");
			if (stringManager.getChannelID() != 0 && stringManager.getChannelPass().isEmpty()) {
				out.println("clientmove clid=0 cid=" + stringManager.getChannelID());
			} else if (stringManager.getChannelID() != 0) {
				out.println("clientmove clid=0 cid=" + stringManager.getChannelID() + " cpw=" + stringManager.getChannelPass());
			}
			if (stringManager.getUseChannel()) out.println("servernotifyregister event=channel id=" + stringManager.getChannelID());
			if (stringManager.getUseTextChannel()) out.println("servernotifyregister event=textchannel id=" + stringManager.getChannelID());
			out.println("clientlist");
			
			socket.setKeepAlive(true);
			if(keepAliveThread != null) keepAliveThread.kill();
			keepAliveThread = new TeamspeakKeepAlive(out);
			keepAliveThread.start();
			
		} catch (Exception e) {
			plugin.getLogger().severe(e.toString());
			e.printStackTrace();
		}
		
	}
	
	public void handleMessage(String message) {
		if (message.startsWith("clid=")) {
			for (String msg : message.split("\\|")) {
				TeamspeakUser user = new ClientlistEvent(plugin, msg).getUser();
				if (user.getClientType() == 0) users.put(user.getID(), user);
			}
		}
		
		String command = message.split(" ")[0];
		message = message.replaceFirst("\\S* ", "");
		if (command.equals("notifycliententerview")) {
			TeamspeakUser user = new EnterEvent(plugin, message).getUser();
			if (user.getClientType() == 0) {
				users.put(user.getID(), user);
			}
		} else if (command.equals("notifyclientleftview")) {
			TeamspeakUser user = new LeaveEvent(plugin, message).getUser();
			if (user != null && user.getClientType() == 0) {
				try {
					users.remove(user.getID());
				} catch (Exception ex) { }
			}
		} else if (command.equals("notifytextmessage")) {
			new ServerMessageEvent(plugin, message);
		} else if (command.equals("notifyclientmoved")) {
			new ClientMovedEvent(plugin, message);
		} else if (command.equals("error") && !message.startsWith("id=0")) {
			new ErrorEvent(plugin, message);
		}
	}
	
	public void kill() {
		this.kill = true;
		out.println("clientupdate");
	}
	
	public Boolean getAlive() {
		return isRunning;
	}
	
	private void setAlive(Boolean alive) {
		if (alive) {
			isRunning = true;
			tsStarted = new Date();
		} else {
			isRunning = false;
			tsStopped = new Date();
		}
	}
	
	public Date getStarted() {
		return tsStarted;
	}
	
	public Date getStopped() {
		return tsStopped;
	}
	
	public void pushMessage(String msg, String sender) {
		if (!(sender.isEmpty() || sender.equals(stringManager.getTeamspeakNickname()))) {
			out.println("clientupdate client_nickname=" + sender);
			out.println(msg);
			out.println("clientupdate client_nickname=" + stringManager.getTeamspeakNickname());
		} else {
			out.println(msg);
		}
	}
	
	public HashMap<Integer, TeamspeakUser> getUsers() {
		return users;
	}
	
	public TeamspeakUser getUserByID(int id) {
		return users.get(id);
	}
	
	public TeamspeakUser getUserByName(String name) {
		TeamspeakUser[] users = this.users.values().toArray(new TeamspeakUser[this.users.values().size()]);
		for(TeamspeakUser currentUser : users){
			if (name.equals(currentUser.getName())) return currentUser;
		}
		return null;
	}
	
	public TeamspeakUser getUserByPartialName(String name) {
		TeamspeakUser[] users = this.users.values().toArray(new TeamspeakUser[this.users.values().size()]);
		for(TeamspeakUser currentUser : users){
			if (currentUser.getName().toLowerCase().startsWith(name.toLowerCase())) return currentUser;
		}
		return null;
	}
}
