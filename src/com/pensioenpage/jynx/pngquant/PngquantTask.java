// Copyright 2007-2009, PensioenPage B.V.
package com.pensioenpage.jynx.pngquant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import static org.apache.tools.ant.Project.MSG_ERR;
import static org.apache.tools.ant.Project.MSG_VERBOSE;
import static org.apache.tools.ant.Project.MSG_WARN;
import org.apache.tools.ant.taskdefs.Execute;
import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;
import org.apache.tools.ant.taskdefs.ExecuteWatchdog;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.taskdefs.PumpStreamHandler;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.util.FileUtils;

/**
 * An Apache Ant task for applying quantization to a number of PNG images,
 * using pngquant. For more information, see the
 * <a href="http://www.libpng.org/pub/png/apps/pngquant.html">pngquant homepage</a>.
 *
 * <p>The most notable parameters supported by this task are:
 *
 * <dl>
 * <dt>command
 * <dd>The name of the command to execute.
 *     Optional, defaults to <code>pngquant</code>.
 *
 * <dt>method
 * <dd>The algorithm to use, either "ordered" or "dithering"
 *     (Floyd-Steinberg). Optional, default is "dithering".
 *
 * <dt>colors
 * <dd>The maximum number of colors for the result image, must be between 2
 *     and 256. Optional, default is 256.
 *
 * <dt>timeOut
 * <dd>The time-out for each individual invocation of the command, in
 *     milliseconds. Optional, defaults to 60000 (60 seconds).
 *
 * <dt>dir
 * <dd>The source directory to read from.
 *     Optional, defaults to the project base directory.
 *
 * <dt>includes
 * <dd>The files to match in the source directory.
 *     Optional, defaults to all files.
 *
 * <dt>overwrite
 * <dd>Flag that can be used to force overwriting of existing newer files.
 *     Optional, defaults to <em>false</em>.
 *
 * <dt>excludes
 * <dd>The files to exclude, even if they are matched by the include filter.
 *     Optional, default is empty.
 *
 * <dt>toDir
 * <dd>The target directory to write to.
 *     Optional, defaults to the source directory.
 * </dl>
 *
 * <p>This task supports more parameters and contained elements, inherited
 * from {@link MatchingTask}. For more information, see
 * <a href="http://ant.apache.org/manual/dirtasks.html">the Ant site</a>.
 *
 * @author <a href="mailto:ernst@pensioenpage.com">Ernst de Haan</a>
 */
public final class PngquantTask extends MatchingTask {

   //-------------------------------------------------------------------------
   // Class fields
   //-------------------------------------------------------------------------

   /**
    * The name of the default pngquant command: <code>"pngquant"</code>.
    */
   public static final String DEFAULT_COMMAND = "pngquant";

   /**
    * The default time-out: 60 seconds.
    */
   public static final long DEFAULT_TIMEOUT = 60L * 1000L;


   //-------------------------------------------------------------------------
   // Class functions
   //-------------------------------------------------------------------------

   /**
    * Returns a quoted version of the specified string,
    * or <code>"(null)"</code> if the argument is <code>null</code>.
    *
    * @param s
    *    the character string, can be <code>null</code>,
    *    e.g. <code>"foo bar"</code>.
    *
    * @return
    *    the quoted string, e.g. <code>"\"foo bar\""</code>,
    *    or <code>"(null)"</code> if the argument is <code>null</code>.
    */
   private static final String quote(String s) {
      return s == null ? "(null)" : "\"" + s + '"';
   }

   /**
    * Returns a quoted version string representation,
    * or <code>"(null)"</code> if the argument is <code>null</code>.
    *
    * @param o
    *    the object, can be <code>null</code>.
    *
    * @return
    *    the quoted string representation of the specified object,
    *    e.g. <code>"\"foo bar\""</code>,
    *    or <code>"(null)"</code> if the argument is <code>null</code>.
    */
   private static final String quote(Object o) {
      return o == null ? "(null)" : quote(o.toString());
   }

   /**
    * Determines if the specified character string matches the regular
    * expression.
    *
    * @param s
    *    the string to research, or <code>null</code>.
    *
    * @param regex
    *    the regular expression, cannot be <code>null</code>.
    *
    * @return
    *    <code>true</code> if <code>s</em> matches the regular expression;
    *    <code>false</code> if it does not.
    *
    * @throws IllegalArgumentException
    *    if <code>regex == null</code> or if it has an invalid syntax. 
    */
   private static final boolean matches(String s, String regex)
   throws IllegalArgumentException {

      // Check preconditions
      if (regex == null) {
         throw new IllegalArgumentException("regex == null");
      }

      // Compile the regular expression pattern
      Pattern pattern;
      try {
         pattern = Pattern.compile(regex);
      } catch (PatternSyntaxException cause) {
         throw new IllegalArgumentException("Invalid regular expression \"" + regex + "\".", cause);
      }

      // Short-circuit if the string is null
      if (s == null) {
         return false;
      }

      // Find a match
      return pattern.matcher(s).find();
   }

   /**
    * Checks if the specified string is either null or empty (after trimming
    * the whitespace off).
    *
    * @param s
    *    the string to check.
    *
    * @return
    *    <code>true</code> if <code>s == null || s.trim().length() &lt; 1</code>;
    *    <code>false</code> otherwise.
    */
   private static final boolean isEmpty(String s) {
      return s == null || s.trim().length() < 1;
   }

   /**
    * Checks if the specified abstract path name refers to an existing
    * directory.
    *
    * @param description
    *    the description of the directory, cannot be <code>null</code>.
    *
    * @param path
    *    the abstract path name as a {@link File} object.
    *
    * @param mustBeReadable
    *    <code>true</code> if the directory must be readable.
    *
    * @param mustBeWritable
    *    <code>true</code> if the directory must be writable.
    *
    * @throws IllegalArgumentException
    *    if <code>location == null
    *          || {@linkplain #isEmpty(String) isEmpty}(description)</code>.
    *
    * @throws BuildException
    *    if <code>  path == null
    *          || ! path.exists()
    *          || ! path.isDirectory()
    *          || (mustBeReadable &amp;&amp; !path.canRead())
    *          || (mustBeWritable &amp;&amp; !path.canWrite())</code>.
    */
   private static final void checkDir(String  description,
                                      File    path,
                                      boolean mustBeReadable,
                                      boolean mustBeWritable)
   throws IllegalArgumentException, BuildException {

      // Check preconditions
      if (isEmpty(description)) {
         throw new IllegalArgumentException("description is empty (" + quote(description) + ')');
      }

      // Make sure the path refers to an existing directory
      if (path == null) {
         throw new BuildException(description + " is not set.");
      } else if (! path.exists()) {
         throw new BuildException(description + " (\"" + path + "\") does not exist.");
      } else if (! path.isDirectory()) {
         throw new BuildException(description + " (\"" + path + "\") is not a directory.");

      // Make sure the directory is readable, if that is required
      } else if (mustBeReadable && (! path.canRead())) {
         throw new BuildException(description + " (\"" + path + "\") is not readable.");

      // Make sure the directory is writable, if that is required
      } else if (mustBeWritable && (! path.canWrite())) {
         throw new BuildException(description + " (\"" + path + "\") is not writable.");
      }
   }


   //-------------------------------------------------------------------------
   // Constructors
   //-------------------------------------------------------------------------

   /**
    * Constructs a new <code>PngquantTask</code> object.
    */
   public PngquantTask() {
      _numColors = 256;
   }


   //-------------------------------------------------------------------------
   // Fields
   //-------------------------------------------------------------------------

   /**
    * The directory to read the image files from.
    * See {@link #setDir(File)}.
    */
   private File _sourceDir;

   /**
    * The directory to write the PNG image files to.
    * See {@link #setToDir(File)}.
    */
   private File _destDir;

   /**
    * Flag that indicates if each existing file should always be overwritten,
    * even if it is newer than the source file. Default is <code>false</code>.
    */
   private boolean _overwrite;

   /**
    * The command to execute. If unset, then this task will attempt to find a
    * proper executable by itself.
    */
   private String _command;

   /**
    * The time-out to apply, in milliseconds, or 0 (or lower) in case no
    * time-out should be applied.
    */
   private long _timeOut;

   /**
    * Character string that indicates whether the files should be processed
    * at all. There are 3 options:
    * <dl>
    * <dt><code>"yes"</code> or <code>"true"</code>
    * <dd>See {@link ProcessOption#MUST}.
    *
    * <dt><code>"no"</code> or <code>"false"</code>
    * <dd>See {@link ProcessOption#MUST_NOT}.
    *
    * <dt><code>"try"</code>
    * <dd>See {@link ProcessOption#SHOULD}.
    * </dl>
    */
   private String _process;

   /**
    * The number of colors to reduce to. Must be between 2 and 256, although
    * the value of this field can be outside this range.
    */
   private int _numColors;

   
   //-------------------------------------------------------------------------
   // Methods
   //-------------------------------------------------------------------------

   /**
    * Sets the path to the source directory. This parameter is required.
    *
    * @param dir
    *    the location of the source directory, or <code>null</code>.
    */
   public void setDir(File dir) {
      log("Setting \"dir\" to: " + quote(dir) + '.', MSG_VERBOSE);
      _sourceDir = dir;
   }

   /**
    * Sets the path to the destination directory. The default is the same
    * directory.
    *
    * @param dir
    *    the location of the destination directory, or <code>null</code>.
    */
   public void setToDir(File dir) {
      log("Setting \"toDir\" to: " + quote(dir) + '.', MSG_VERBOSE);
      _destDir = dir;
   }

   /**
    * Sets the <em>overwrite</em> flag.
    *
    * @param flag
    *    the value for the flag.
    */
   public void setOverwrite(boolean flag) {
      _overwrite = flag;
   }

   /**
    * Sets the command to execute, optionally. By default this task will find
    * a proper command on the current path.
    *
    * @param command
    *    the command to use, e.g. <code>"/usr/local/bin/pngquant"</code>,
    *    can be <code>null</code> (in which case the task will find the command).
    */
   public void setCommand(String command) {
      log("Setting \"command\" to: " + quote(command) + '.', MSG_VERBOSE);
      _command = command;
   }

   /**
    * Configures the time-out for executing a single command. The
    * default is 60 seconds. Setting this to 0 or lower disables the time-out
    * completely.
    *
    * @param timeOut
    *    the time-out to use in milliseconds, or 0 (or lower) if no time-out
    *    should be applied.
    */
   public void setTimeOut(long timeOut) {
      log("Setting \"timeOut\" to: " + timeOut + " ms.", MSG_VERBOSE);
      _timeOut = timeOut;
   }

   /**
    * Sets whether the files should be processed at all.
    * There are 3 options:
    * <dl>
    * <dt><code>"yes"</code> or <code>"true"</code>
    * <dd>The files <em>must</em> be processed.
    *     If the command is unavailable or if it fails to process any of the
    *     files, then this task fails.
    *
    * <dt><code>"no"</code> or <code>"false"</code>
    * <dd>The files must <em>not</em> be processed by the command but must
    *     instead just be copied as-is, unchanged.
    *
    * <dt><code>"try"</code>
    * <dd>If the command is available then process the file(s), but if the
    *     command is unavailable or if it fails to process the file(s), then
    *     just copy the files instead (a warning will then be output).
    * </dl>
    *
    * @param s
    *    the value, should be one of the allowed values (otherwise the task
    *    will fail during execution).
    */
   public void setProcess(String s) {
      log("Setting \"process\" to: " + quote(s) + '.', MSG_VERBOSE);
      _process = s;
   }

   /**
    * Sets the number of colors to reduce the color palette to. Must be
    * between 2 and 256.
    *
    * @param numColors
    *    the number of colors.
    */
   public void setColors(int numColors) {
      _numColors = numColors;
   }

   @Override
   public void execute() throws BuildException {

      // Source directory defaults to current directory
      if (_sourceDir == null) {
         _sourceDir = getProject().getBaseDir();
      }

      // Destination directory defaults to source directory
      if (_destDir == null) {
         _destDir = _sourceDir;
      }

      // Check the directories
      checkDir("Source directory",      _sourceDir,  true, false);
      checkDir("Destination directory",   _destDir, false,  true);

      // Interpret the "process" option
      ProcessOption processOption;
      String p = (_process == null) ? null : _process.toLowerCase().trim();
      if (p == null || "true".equals(p) || "yes".equals(p)) {
         processOption = ProcessOption.MUST;
      } else if ("false".equals(_process) || "no".equals(_process)) {
         processOption = ProcessOption.MUST_NOT;
      } else if ("try".equals(_process)) {
         processOption = ProcessOption.SHOULD;
      } else {
         throw new BuildException("Invalid value for \"process\" option: " + quote(_process) + '.');
      }

      // Determine what command to execute
      String command = (_command == null || _command.length() < 1)
                     ? DEFAULT_COMMAND
                     : _command;

      // Test that the command is available
      boolean commandAvailable = testCommand(command, processOption);

      // Determine if transformation should be attempted at all
      // (alternative is just copying)
      boolean transform = processOption != ProcessOption.MUST_NOT && commandAvailable;

      // Determine the number of colors
      if (_numColors < 2) {
         throw new BuildException("Number of colors (" + _numColors + ") is invalid, it is too low. It should be between 2 and 256.");
      } else if (_numColors > 256) {
         throw new BuildException("Number of colors (" + _numColors + ") is invalid, it is too high. It should be between 2 and 256.");
      }

      // Consider each individual file for processing/copying
      log("Transforming from " + _sourceDir.getPath() + " to " + _destDir.getPath() + '.', MSG_VERBOSE);
      long start = System.currentTimeMillis();
      int failedCount = 0, processCount = 0, copyCount = 0, skippedCount = 0;
      for (String inFileName : getDirectoryScanner(_sourceDir).getIncludedFiles()) {

         long thisStart = System.currentTimeMillis();

         // Make sure the input file exists
         File inFile = new File(_sourceDir, inFileName);
         if (! inFile.exists()) {
            continue;
         }

         // Determine if the file type is supported
         if (! matches(inFileName.toLowerCase(), "\\.png$")) {
            log("Skipping " + quote(inFileName) + " because the file does not end in \".png\" (case-insensitive).", MSG_VERBOSE);
            skippedCount++;
            continue;
         }

         // Some preparations related to the input file and output file
         String outFileName = inFileName.replaceFirst("\\.[a-zA-Z]+$", ".png");
         File       outFile = new File(_destDir, outFileName);
         String outFilePath = outFile.getPath();
         String  inFilePath = inFile.getPath();

         // Skip this file is the output file exists and is newer
         if (!_overwrite && outFile.exists() && (outFile.lastModified() > inFile.lastModified())) {
            log("Skipping " + quote(inFileName) + " because output file is newer.", MSG_VERBOSE); 
            skippedCount++;
            continue;

         // Skip each empty file
         } else if (inFile.length() < 1L) {
            log("Skipping " + quote(inFileName) + " because the file is completely empty.", MSG_WARN); 
            skippedCount++;
            continue;
         }

         // File transformation should be attempted
         boolean copy = !transform;
         if (transform) {

            boolean     failure = false;
            String errorMessage = null;
            Throwable    caught = null;

            // Create temporary input file
            File tempInFile = null;
            try {
               tempInFile = File.createTempFile(getClass().getSimpleName(), ".png");
               log("Created temporary input file \"" + tempInFile.getPath() + "\".", MSG_VERBOSE);
            } catch (Throwable exception) {
               failure      = true;
               errorMessage = "Failed to create temporary input file.";
               caught       = exception;
            }

            if (! failure) try {

               // TODO: Send stdout output to a NullOutputStream

               // Create stream to out/error buffer
               ByteArrayOutputStream outStream = new ByteArrayOutputStream();
               ByteArrayOutputStream errStream = new ByteArrayOutputStream();
 
               // Prepare for the command execution
               PumpStreamHandler streamHandler = new PumpStreamHandler(outStream, errStream);
               ExecuteWatchdog        watchdog = (_timeOut > 0L) ? new ExecuteWatchdog(_timeOut) : null;
               Execute                 execute = new Execute(streamHandler, watchdog);
               String[]                cmdline = new String[] { command, String.valueOf(_numColors), tempInFile.getPath() };

               initExecute(execute, cmdline);

               // Execute the command
               try {
                  execute.execute();
                  failure = execute.isFailure();
               } catch (IOException exception) {
                  failure = true;
                  caught  = exception;
               }

               String  tempInFileName = tempInFile.getName();
               String tempOutFileName = tempInFileName.substring(0, tempInFileName.length() - 4) + "-fs8.png";
               File       tempOutFile = new File(tempInFile.getParent(), tempInFile.getName().substring(0, tempInFile.getName().length() - 4));

               // Output to stderr indicates a failure
               errorMessage = errStream.toString();
               if (! isEmpty(errorMessage)) {
                  failure = true;

               // Empty output also indicates failure
               } else if (! failure && !tempOutFile.exists()) {
                  failure      = true;
                  errorMessage = "No output produced.";

               } else if (! failure && tempOutFile.length() < 1L) {
                  failure      = true;
                  errorMessage = "No output produced.";
                  deleteFile(tempOutFile);

               // Copy the temporary output file to the target location
               } else try {
                  FileUtils.getFileUtils().copyFile(tempOutFile, outFile);
               } catch (Throwable exception) {
                  failure      = true;
                  errorMessage = "Failed to copy " + quote(tempOutFile.getPath()) + " to " + quote(outFile.getPath()) + '.';
                  caught       = exception;
                  deleteFile(tempOutFile);
                  deleteFile(outFile);
               }
            } finally {
               tempInFile.delete();
            }

            // Log the result for this individual file
            long thisDuration = System.currentTimeMillis() - thisStart;
            if (failure) {
               String logMessage = "Failed to process " + quote(inFilePath) + " (took " + thisDuration + " ms)";
               if (isEmpty(errorMessage)) {
                  logMessage += '.';
               } else {
                  logMessage += ": " + errorMessage;
               }
               log(logMessage, MSG_ERR);
               failedCount++;

               // Failed, but then instead copy the input file unchanged
               if (processOption != ProcessOption.MUST) {
                  copy = true;
               }
            } else {
               log("Processed " + quote(inFileName) + " in " + thisDuration + " ms.", MSG_VERBOSE);
               processCount++;
            }
         }

         // Copy the file?
         if (copy) {
            try {
               FileUtils.getFileUtils().copyFile(inFile, outFile);
               long thisDuration = System.currentTimeMillis() - thisStart;
               log("Copied " + quote(inFileName) + " in " + thisDuration + " ms.", MSG_VERBOSE);
               copyCount++;
            } catch (Throwable exception) {
               String logMessage = "Failed to copy " + quote(inFilePath) + " to " + quote(outFilePath) + '.';
               log(logMessage, MSG_ERR);
               failedCount++;
            }
         }
      }

      // Log the total result
      long duration = System.currentTimeMillis() - start;
      if (failedCount > 0) {
         throw new BuildException("" + failedCount + " file(s) failed to be processed and/or copied; " + processCount + " file(s) processed; " + copyCount + " file(s) copied; " + skippedCount + " file(s) skipped. Total duration is " + duration + " ms.");
      } else {
         log("" + processCount + " file(s) processed and " + copyCount + " file(s) copied in " + duration + " ms; " + skippedCount + " file(s) skipped.");
      }
   }

   private void initExecute(Execute execute, String[] cmdline) {
      execute.setAntRun(getProject());
      execute.setCommandline(cmdline);

      String logMessage = cmdline[0];
      for (int i = 1; i < cmdline.length; i++) {
         logMessage += " " + cmdline[i];
      }
      log("Command line: " + quote(logMessage) + '.', MSG_VERBOSE);
   }

   private void deleteFile(File f) {
      try {
         f.delete();
      } catch (Throwable e) {
         log("Failed to delete file \"" + f.getPath() + "\".", MSG_ERR);
      }
   }

   private boolean testCommand(String command, ProcessOption processOption)
   throws IllegalArgumentException, BuildException {

      // Check preconditions
      if (command == null) {
         throw new IllegalArgumentException("command == null");
      } else if (processOption == null) {
         throw new IllegalArgumentException("processOption == null");
      }

      // Short-circuit if no command should be executed
      if (processOption == ProcessOption.MUST_NOT) {
         return false;
      }

      // Create a watch dog, if a time-out is configured
      ExecuteWatchdog watchdog = (_timeOut > 0L) ? new ExecuteWatchdog(_timeOut) : null;

      // Prepare for command execution
      ByteArrayOutputStream    outAndErr = new ByteArrayOutputStream();
      ExecuteStreamHandler streamHandler = new PumpStreamHandler(outAndErr);
      Execute                    execute = new Execute(streamHandler, watchdog);
      String[]                   cmdline = new String[] { command };

      initExecute(execute, cmdline);

      // Attempt command execution
      Throwable caught;
      try {
         execute.execute();
         caught = null;
      } catch (Throwable e) {
         caught = e;
      }

      // Executing the command triggered an exception
      boolean commandAvailable;
      if (caught != null) {
         String message = "Unable to execute command " + quote(command) + '.';
         if (processOption == ProcessOption.MUST) {
            throw new BuildException(message, caught);
         } else {
            log(message, MSG_ERR);
            commandAvailable = false;
         }

      // Executing the command resulted in a exit code other than 0 or 1,
      // indicating failure
      // NOTE: There is no way to determine the version of pngquant without
      //       pngquant returning 1 from the command, d'oh
      } else if (execute.getExitValue() != 0 && execute.getExitValue() != 1) {
         String message = "Unable to execute command " + quote(command) + ". Running '" + command + "' resulted in exit code " + execute.getExitValue() + '.';
         if (processOption == ProcessOption.MUST) {
            throw new BuildException(message);
         } else {
            log(message, MSG_ERR);
            commandAvailable = false;
         }

      // Command was executed successfully
      // NOTE: The version information is sent to stderr instead of stdout,
      //       there seems to be no way in pngquant 1.0 to change this, d'oh
      } else {
         Pattern pattern = Pattern.compile("^[^0-9]*([0-9]+(\\.[0-9]+)*)");
         Matcher matcher = pattern.matcher(outAndErr.toString());
         if (! matcher.find()) {
            log("Unable to execute command " + quote(command) + ". No version output found (on stderr) when running the command without arguments.", MSG_ERR);
            commandAvailable = false;
         } else {
            String version = quote(matcher.group(1));
            log("Using command " + quote(command) + ", version is " + version + '.', MSG_VERBOSE);
            commandAvailable = true;
         }
      }

      return commandAvailable;
   }


   //-------------------------------------------------------------------------
   // Inner classes
   //-------------------------------------------------------------------------

   /**
    * Enumeration type for the different process options.
    *
    * @author <a href="mailto:ernst@pensioenpage.com">Ernst de Haan</a>
    */
   private enum ProcessOption {

      /**
       * Force processing: if the command is unavailable or fails to execute
       * successfully, then this task will fail.
       */
      MUST,
         
      /**
       * Skip processing completely: just copy the files.
       */
      MUST_NOT,

      /**
       * Attempt processing, but fallback to copying if processing fails.
       * The task will only fail if the copying also fails to succeed.
       */
      SHOULD;
   }
}
