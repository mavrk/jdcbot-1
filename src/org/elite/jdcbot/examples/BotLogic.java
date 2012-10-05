package org.elite.jdcbot.examples;

import java.io.*;
import java.util.*;

import org.elite.jdcbot.framework.*;
import org.elite.jdcbot.shareframework.*;
import org.elite.jdcbot.util.GlobalFunctions;

public class BotLogic /*extends EventjDCBotAdapter*/ implements ShareManagerListener {
	// bot data
	private BotConfig config;
	private jDCBot bot;
	private MultiHubsAdapter mha = null;
	protected ShareManager shareManager;
	private DownloadCentral dc;
	private List<File> includes = new ArrayList<File>();
	private List<File> excludes = new ArrayList<File>();
	private List<String> removes = new ArrayList<String>();
	
	/*public BotLogica(MultiHubsAdapter multiHubsAdapter) throws IOException,
	BotException {
		super(multiHubsAdapter);
	}*/

	@Override
	public void onFilelistDownloadFinished(User u, boolean success, Exception e) {
		if (success) {
			FileListManager flm = shareManager.getOthersFileListManager(u);
			String out =
				"File list from " + u.username() + "\n" + flm.getFilelist().printTree() + "\nFiles shared size = "
				+ GlobalFunctions.trimDecimals(flm.getFilelist().getSize(false) / 1024 / 1024, 2) + " MB"
				+ "\nClient ID = " + flm.getFilelist().getCID();
			System.out.println(out);
		} else {
			System.out.println("The user file list download from " 
			+ u.username() + " failed. Got the exception: " + e.getMessage());
		}
	}

	@Override
	public void hashingOfFileStarting(String file) {
		System.out.println("Starting hash in file: " + file);
	}

	@Override
	public void hashingJobFinished() {
		System.out.println("The hash process has finished");
	}

	@Override
	public void hashingOfFileComplete(String f, boolean success, HashException e) {
		System.out.println("Hashing of " + f + " " + (success ? "is complete."
						: " failed due to exception: " + e.getMessage()));
	}

	@Override
	public void hashingOfFileSkipped(String f, String reason) {
		System.out.println("Hashing of  " + f + "skipped because " + reason);
	}

	@Override
	public void onMiscMsg(String msg) {
		System.out.println("Error: " + msg);
	}

	public void configBot(String nombreBot, String iPBot,
			int listenPort, int listenPortUDP, String password,
			String description, String connectionType, String email,
			String shareSize, int uploadSlots, int downloadSlots,
			boolean isPassive, String dirConfig, String dirOthers) {

		config = new BotConfig();
		config.setBotname(nombreBot);
		config.setBotIP(iPBot);
		config.setListenPort(listenPort);
		config.setUDP_listenPort(listenPortUDP);
		config.setPassword(password);
		config.setDescription(description);
		config.setConn_type(connectionType + User.NORMAL_FLAG);
		config.setEmail(email);
		config.setSharesize(shareSize);
		config.setUploadSlots(uploadSlots);
		config.setDownloadSlots(downloadSlots);
		config.setPassive(isPassive);
		
		boolean settingDirsSuccess = false;

		try {
			mha = new MultiHubsAdapter(config);

			mha.setDirs(dirConfig, dirOthers); // this must exist
			settingDirsSuccess = true;

			if (!settingDirsSuccess) {
				System.out.println("Setting of directories was not successfull. Aborting.");
				disconnect();
				return;
			}

			shareManager = new ShareManager(mha);
			dc = new DownloadCentral(mha);

			mha.setShareManager(shareManager);
			mha.setDownloadCentral(dc);

			shareManager.addListener(this);
			
			// creates the bot with the previous config
			bot = new jDCBot(mha) {
			};
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** Connects with the server */
	public void connect(String server, int serverPort) {

		try {
			mha.connect(server, serverPort, bot);
			Thread.sleep(3000); // time to get the user's info
			bot.SendPublicMessage(bot.getBotName() + " is connected.");

		} catch (IOException e) {
			System.out.println("Can't find the server");
			e.printStackTrace();
			disconnect();
		} catch (BotException e) {
			e.printStackTrace();
			disconnect();
		} catch (InterruptedException e) {
			e.printStackTrace();
			disconnect();	
		}
	}

	/** Close the connection */
	public void disconnect() {
		try {
			mha.terminate();
			//bot.terminate();
		} catch (BotException e){
			e.printStackTrace();
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**	Search for an user in the hub's user list */
	public User searchUser(String usr) {
		if (!mha.UserExist(usr)) {
			System.out.println("Couldn't find the user " + usr);
			return null;
		}
		List<User> u = mha.getUsers(usr);
		if (u.size() > 1)
			System.out.println("Getting the user connected to: " + u.get(0).getHubSignature() + ".");
		return u.get(0);
	}

	/** Reads another user file list */
	public void readFiles(String usr) {
		try {
			User u = searchUser(usr);
			if (u != null) {
				System.out.println("Reading file list from " + usr);
				shareManager.downloadOthersFileList(u);
			}
		} catch (BotException e) {
			e.printStackTrace();
		}
	}

	/** Search a file in the hub */
	public void search(String searchText) {
		SearchSet s = new SearchSet();
		s.string = searchText;
		try {
			mha.Search(s);
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Error: " + e.getMessage());
		}
	}

	/** Add the files to share from a file path given */
	public void addDirectory(String filePath) {
		try {
			File f = new File(filePath);
			includes.add(f);
			System.out.println("The " + filePath + " has been added.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Excludes a file path */
	public void excludeDirectory(String filePath) {
		try {
			File f = new File(filePath);
			excludes.add(f);
			System.out.println("The " + filePath + " has been excluded.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Removes a previous added directory */
	public void removeDirectory(String filePath) {
		try {
			removes.add(filePath);
			System.out.println(filePath + " added to excludes list.");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** Procees all the list (add, exclude, removes) then
		start hash process
	 */
	public void processList() {
		try {
			shareManager.addShare(new ArrayList<File>(includes),
					new ArrayList<File>(excludes), new FilenameFilter() {

						public boolean accept(File dir, String name) {
							if ((GlobalFunctions.isWindowsOS() && new File(dir
									+ File.separator + name).isHidden())
									|| (!GlobalFunctions.isWindowsOS() && name
											.startsWith("."))){ // doesn't share hidden files
								return false;
								}
							else{
								return true;
							}
						}
					}, null);

			shareManager.removeShare(new ArrayList<String>(removes));

			includes.clear();
			excludes.clear();
			removes.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** Check the hashing process*/
	public void hashstat(){
		System.out.println("\nHashing: " + shareManager.getCurrentlyHashedFileName() + "\n%Complete: "
				+ shareManager.getPercentageHashCompletion() + "%\nHashing speed: "
				+ GlobalFunctions.trimDecimals(shareManager.getHashingSpeed() / 1024 / 1024, 2) + " MBps\nRemaining time: "
				+ GlobalFunctions.trimDecimals(shareManager.getTimeLeft2CompleteHashing() / 60, 2) + " min(s)");
	}

	// getflnode ($own|<username>) <path> - Displays information about any
	// directory or file in the filelist of bot's (if $own is given) or
	// other user's.\n"

	// searchfl ($own|<username>) <term> - Searches for term in the bot's
	// own file list or downloaded file lists of other users.\n"

	// download <magnet uri> - It will automatically search for files
	// mathcing this magnet URI and download it.\n"
	
	/** Download the file using uri*/
	public void downloadFile (String uri)
	{
		Query Q[] = getSegmentedQuery(uri.substring(uri.indexOf('?') + 1));
		if (Q == null) {
			System.out.println("Error! Maybe the URI is not in proper format");
			return;
		}
		
		String tth = null, name = null;
		long size = 0;
		for (Query q : Q) {
			if (q.query.equalsIgnoreCase("xt")) {
				tth = q.value.substring(q.value.lastIndexOf(':') + 1);
			} else if (q.query.equalsIgnoreCase("xl")) {
				try {
					size = Long.parseLong(q.value);
				} catch (NumberFormatException e) {
					System.out.println("Please enter a valid magnet uri. Error occured while trying to parse file size");
					return;
				}
			} else if (q.query.equalsIgnoreCase("dn")) {
				name = q.value.replace('+', ' ');
			}
		}

		if (size <= 0) {
			System.out.println("Invalid value of file size: " + size + ". Make sure you have entered a valid magnet uri.");
			return;
		}
		if (tth == null || name == null) {
			System.out.println("Error occured during parsing the magnet uri. Make sure this is valid.");
			return;
		}
		
		File file = new File(name);
		if (file.exists()) {
			System.out.println("Cannot download. A file with this name already exists in download directory.");
			return;
		}

		try {
			dc.download(tth, true, size, file, null);
		} catch (BotException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	// dstat [<magnet uri>] - This simple command simply show the various
	// stats realting the download of file represented by the given magnet
	// URI.\n"

	// cancel (download|upload|hash) [<magnet uri>] - If any file is being
	// downloaded with this magnet URI then it will be cancelled. Arguemtn
	// options 'upload' and 'hash' doesn't requir magnet URI argument.
	// 'upload' will cancel all running uploads and 'hash' will cancel the
	// hashing.\n"
	
	private Query[] getSegmentedQuery(String query) {
		List<Query> Q = new ArrayList<Query>();
		String qs[] = query.split("&");
		for (String q : qs) {
			String e[] = q.split("=");
			Q.add(new Query(e[0], e.length < 2 ? null : e[1]));
		}
		return Q.toArray(new Query[0]);
	}
	
	private class Query {
		public Query(String q, String v) {
			query = q;
			value = v;
		}

		public String query;
		public String value;

		public String toString() {
			return query + " = " + value;
		}
	}
}
