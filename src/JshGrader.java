
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

/**
 * A class to automate checking of pa2 - Jsh1.
 */
public class JshGrader {

  private File workingDir;

  private volatile StringBuilder jshOut;
  private volatile StringBuilder bashOut;

  private HashMap<String, Integer> jshOutMap;
  private HashMap<String, Integer> bashOutMap;

  /**
   * Constructor Method
   * @param workingDir The working directory.
   */
  public JshGrader(File workingDir) {
    this.workingDir = workingDir;

    this.jshOut = new StringBuilder("");
    this.bashOut = new StringBuilder("");

    this.jshOutMap = new HashMap<>();
    this.bashOutMap = new HashMap<>();
  }


  /**
   * The main method.
   * @param args The args arguments.
   */
  public static void main(String[] args) {

    if (args.length == 1) {
      JshGrader checker = new JshGrader(new File(args[0]));
      checker.execute();
    }
    else {

      //JshGrader checker = new JshGrader(new File("/Users/rckndn/Desktop/student"));
      //checker.execute();

      System.err.println("Usage: java JshGrader <working_directory");
      System.exit(-1);
    }

  }

  /**
   * Compiles and tests the submitted code.
   */
  private void execute() {

    boolean bPassed;
    ArrayList<String> testCommands = new ArrayList<>();

    compileFiles();

    System.out.println("Compile Successful.");

    testCommands.add("pwd");
    testCommands.add("ls");
    testCommands.add("java FlipFlop 10 20");

    for (String testCommand : testCommands) {
      System.out.print("Testing '" + testCommand + "': ");
      bPassed = testCommand(testCommand);
      System.out.println((bPassed ? "Passed" : "Failed"));
      if (!bPassed) {
        printFailures();
        System.exit(-1);
      }
    }

  }


  private String cleanString(String word) {
    ArrayList<String> stopWords = new ArrayList<>();

    stopWords.add("jsh>");
    stopWords.add("jsh >");
    stopWords.add("^d");
    stopWords.add("exit");
    stopWords.add(" ");

    for (String s : stopWords) {
      if (word.contains(s)) {
        word = word.replace(s, "");
      }
    }

    return word;
  }

  private boolean testCommand(String command) {

    // JSH
    CommandExecutor jshExecutor = new CommandExecutor(this.workingDir, ProcessName.JSH, command);
    jshExecutor.start();

    // BASH
    CommandExecutor bashExecutor = new CommandExecutor(this.workingDir, ProcessName.BASH, command);
    bashExecutor.start();

    try {
      jshExecutor.join();
      bashExecutor.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
      return false;
    }

    // Compare Jsh and Bash output
    String line;
    int count;

    line = jshOut.toString().trim().toLowerCase();
    String[] tokens = line.split("[ \t\n]+");

    for (String s : tokens) {
      String word = cleanString(s);
      if (word.length() > 0) {
        count = 1;
        if (jshOutMap.containsKey(word)) {
          count = jshOutMap.get(word);
          count++;
        }
        jshOutMap.put(word, count);
      }
    }

    line = bashOut.toString().trim().toLowerCase();
    tokens = line.split("[ \t\n]+");

    for (String s : tokens) {
      String word = cleanString((s));
      if (word.length() > 0) {
        count = 1;
        if (bashOutMap.containsKey(word)) {
          count = bashOutMap.get(word);
          count++;
        }
        bashOutMap.put(word, count);
      }
    }

    // Compare maps
    Iterator it = jshOutMap.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next().toString();
      if (bashOutMap.containsKey(s)) {
        if (jshOutMap.get(s).equals(bashOutMap.get(s))) {
          it.remove();
          bashOutMap.remove(s);
        }
      }
    }

    // If both maps are empty then Jsh and Bash produced the same output.
    return (jshOutMap.isEmpty() && bashOutMap.isEmpty());

  }


  private void printFailures() {

    String div = "==============================================================";

    System.out.println(div);

    System.out.println("JSH output: " + jshOut);
    if (!jshOutMap.isEmpty()) {
      System.out.print("  - ");
      for (String s : jshOutMap.keySet()) {
        System.out.print(s + " ");
      }
    }

    System.out.println("\n" + div);

    System.out.println("BASH output: " + bashOut);
    if (!bashOutMap.isEmpty()) {
      System.out.print("  - ");
      for (String s : bashOutMap.keySet()) {
        System.out.print(s + " ");
      }
    }
    System.out.println("\n" + div);
  }

  private void compileFiles() {

    // Does the directory exist?
    if (!workingDir.exists()) {
      System.err.println("No such directory: " + workingDir.getPath());
      System.exit(-1);
    }

    // Are there files in the directory?
    if (workingDir.list().length == 0) {
      System.err.println("There are no files in the directory.");
      System.exit(-1);
    }

    // Compile Each File
    ProcessBuilder pb;
    Process p = null;

    // Delete all .class files
    for (File file : workingDir.listFiles()) {
      if (file.getPath().endsWith(".class")) {
        file.delete();
      }
    }

    for (File file : workingDir.listFiles()) {

      if (file.getPath().endsWith(".java")) {

        pb = new ProcessBuilder("javac", file.getPath());

        try {
          p = pb.start();
        } catch (IOException e) {
          System.err.println("Error starting ProcessBuilder");
          System.exit(-1);
        }

        // Collect output

        try {
          p.waitFor();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    } // End of for loop
  }


  public class CommandExecutor extends Thread {

    private File workingDir;
    private String command;
    private ProcessName procName;

    private Process p = null;

    public CommandExecutor(File workingDir, ProcessName procName, String command) {
      this.workingDir = workingDir;
      this.command = command;
      this.procName = procName;
    }

    public void run() {

      ProcessBuilder pb = null;

      switch (procName) {
        case JSH:
          pb = new ProcessBuilder("java", "Jsh");
          break;
        case BASH:
          String[] tokens = command.trim().split("[ \t\n]+");
          pb = new ProcessBuilder(tokens);
          break;
        default:
          break;
      }

      pb.directory(this.workingDir);

      try {
        p = pb.start();
      } catch (IOException e) {
        if (procName == ProcessName.JSH) {
          System.err.println("Error Creating JSH instance.");
          System.exit(-1);
        }
        // Ignore problems creating BASH instances.
      }

      if (procName == ProcessName.JSH) {

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));

        try {
          writer.write(command);
          writer.flush();
          writer.close();
        } catch (IOException e) {
          System.err.println("Error writing command to process.");
          System.exit(-1);
        }

      }

      BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
      BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()));

      String line;

      ThreadKiller killer;

      switch (procName) {
        case JSH:

          jshOut.setLength(0);

          // Output Stream

          killer = new ThreadKiller(this, KillerTask.CLOSE_OUTPUT);
          killer.start();

          try {
            while ((line = stdout.readLine()) != null) {
              line += " ";
              jshOut.append(line);
            }
          }
          catch (IOException e) {
            // Do Nothing
          }

          // Error Stream
          killer = new ThreadKiller(this, KillerTask.CLOSE_ERROR);
          killer.start();
          try {
            while ((line = stderr.readLine()) != null) {
              line += " ";
              jshOut.append(line);
            }
          }
          catch (IOException e) {
            // Do Nothing
          }

          break;

        case BASH:

          bashOut.setLength(0);

          // Output Stream
          try {
            while ((line = stdout.readLine()) != null) {
              line += " ";
              bashOut.append(line);
            }
          }
          catch (IOException e) {
            // Do Nothing
          }

          // Error Stream
          try {
            while ((line = stderr.readLine()) != null) {
              line += " ";
              bashOut.append(line);
            }
          }
          catch (IOException e) {
            // Do Nothing
          }

          break;

        default:
          System.err.println("Error in CommandExecutor class.");
          break;
      }

      try {
        killer = new ThreadKiller(this, KillerTask.KILL);
        killer.start();
        p.waitFor();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

    }

    public void closeInputStream() {
      try {
        p.getInputStream().close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void closeErrortStream() {
      try {
        p.getErrorStream().close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }

    public void destroyProcess() {
      p.destroy();
    }

  }

  private class ThreadKiller extends Thread {

    CommandExecutor victim;
    KillerTask task;

    // Constructor
    public ThreadKiller(CommandExecutor victim, KillerTask task) {
      this.victim = victim;
      this.task = task;
    }

    public void run() {
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }

      if (victim.getState() == State.RUNNABLE || victim.getState() == State.WAITING) {
        if (this.task == KillerTask.CLOSE_OUTPUT) {
          victim.closeInputStream();
        }
        else if(this.task == KillerTask.CLOSE_ERROR) {
          victim.closeErrortStream();
        }
        else if (this.task == KillerTask.KILL) {
          victim.destroyProcess();
        }
      }

    }

  }

  private enum ProcessName {
    JSH,
    BASH
  }
  private enum KillerTask {
    CLOSE_OUTPUT,
    CLOSE_ERROR,
    KILL
  }

}
