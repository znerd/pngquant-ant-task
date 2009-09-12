// Copyright 2007-2009, PensioenPage B.V.
package com.pensioenpage.jynx.pngquant;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.tools.ant.taskdefs.ExecuteStreamHandler;
import org.apache.tools.ant.taskdefs.StreamPumper;

/**
 * An <code>ExecuteStreamHandler</code> implementation that stores all
 * output in a buffer.
 *
 * @version $Revision: 10190 $ $Date: 2009-08-25 17:49:35 +0200 (di, 25 aug 2009) $
 * @author <a href="mailto:ernst@pensioenpage.com">Ernst de Haan</a>
 */
class Buffer extends Object implements ExecuteStreamHandler {

   // TODO: Add state checking

   //-------------------------------------------------------------------------
   // Constructors
   //-------------------------------------------------------------------------

   /**
    * Constructs a new <code>Buffer</code>.
    */
   public Buffer() {
      // empty
   }


   //-------------------------------------------------------------------------
   // Fields
   //-------------------------------------------------------------------------

   /**
    * The stream to read all <em>stdin</em> input from.
    * Initially <code>null</code>.
    */
   private InputStream _in;

   /**
    * The stream to send all <em>stdout</em> output to.
    * Never <code>null</code>.
    */
   private OutputStream _out;

   /**
    * The buffer holding all <em>stderr</em> output. Never <code>null</code>.
    */
   private OutputStream _err;

   /**
    * The thread pumping input to the <em>stdin</em> input.
    * Initially <code>null</code>, set by
    * {@link #setProcessInputStream(OutputStream)}.
    * The thread is started from the {@link #start()} method.
    */
   private Thread _inThread;

   /**
    * The thread pumping the <em>stdout</em> output to the buffer.
    * Initially <code>null</code>, set by
    * {@link #setProcessOutputStream(InputStream)}.
    * The thread is started from the {@link #start()} method.
    */
   private Thread _outThread;

   /**
    * The thread pumping the <em>stderr</em> output to the buffer.
    * Initially <code>null</code>, set by
    * {@link #setProcessErrorStream(InputStream)}.
    * The thread is started from the {@link #start()} method.
    */
   private Thread _errThread;


   //----------------------------------------------------------------------
   // Methods
   //----------------------------------------------------------------------

   public void setInputFile(File inFile)
   throws IllegalArgumentException,
          FileNotFoundException,
          SecurityException {

      // Check preconditions
      if (inFile == null) {
         throw new IllegalArgumentException("inFile == null");
      }

      // Create a stream from the file
      _in = new FileInputStream(inFile);
   }

   public void redirectOutputTo(File outFile)
   throws IllegalArgumentException,
          FileNotFoundException,
          SecurityException {

      // Check preconditions
      if (outFile == null) {
         throw new IllegalArgumentException("outFile == null");
      }

      // Create a stream to the file, creating it in the process
      _out = new FileOutputStream(outFile);
   }

   // Specified by ExecuteStreamHandler
   public void setProcessInputStream(OutputStream os) {
      if (_in != null) {
         _inThread = new Thread(new StreamPumper(_in, os));
      }
   }

   // Specified by ExecuteStreamHandler
   public void setProcessOutputStream(InputStream is) {
      if (_out == null) {
         _out = new ByteArrayOutputStream();
      }
      _outThread = new Thread(new StreamPumper(is, _out));
   }

   // Specified by ExecuteStreamHandler
   public void setProcessErrorStream(InputStream is) {
      if (_err == null) {
         _err = new ByteArrayOutputStream();
      }
      _errThread = new Thread(new StreamPumper(is, _err));
   }

   // Specified by ExecuteStreamHandler
   public void start() {
      if (_inThread != null) {
         _inThread.start();
      }
      _outThread.start();
      _errThread.start();
   }

   // Specified by ExecuteStreamHandler
   public void stop() {
      join( _inThread);
      join(_outThread);
      join(_errThread);

      finish(_out);
      finish(_err);
   }

   private void join(Thread thread) {

      if (thread != null) {

         // Join the thread, ignore errors
         try {
            thread.join();
         } catch (InterruptedException e) {
            // ignore
         }
      }
   }

   private void finish(OutputStream out) throws IllegalArgumentException {

      if (out == null) {
         throw new IllegalArgumentException("out == null");
      }

      try {
         out.flush();
      } catch (Throwable e) {
         // ignore
      }

      try {
         out.close();
      } catch (Throwable e) {
         // ignore
      }
   }

   /**
    * Determines the number of bytes output to <em>stdout</em>.
    *
    * @return
    *    the number of bytes output to <em>stdout</em>.
    */
   public long getOutSize() {
      return getSize(_out);
   }

   private long getSize(OutputStream stream) throws IllegalArgumentException {
      if (stream == null) {
         throw new IllegalArgumentException("stream == null");
      }

      if (stream instanceof ByteArrayOutputStream) {
         return ((ByteArrayOutputStream) stream).size();
      } else if (stream instanceof FileOutputStream) {
         try {
            return ((FileOutputStream) stream).getChannel().size();
         } catch (IOException cause) {
            throw new Error("Failed to determine size of file.", cause);
         }
      } else {
         throw new Error("Internal error: Unrecognized class " + stream.getClass().getName() + '.');
      }
   }

   /**
    * Copies all collected <em>stdout</em> output to the specified output
    * stream.
    *
    * @param target
    *    the {@link OutputStream} to copy the collected <em>stdout</em> output
    *    to, cannot be <code>null</code>.
    *
    * @throws IllegalArgumentException
    *    if <code>target == null</code>.
    *
    * @throws IOException
    *    in case of an I/O error.
    */
   public void writeOutTo(OutputStream target)
   throws IllegalArgumentException, IOException {
      copy(_out, target);
   }

   /**
    * Retrieves all collected <em>stdout</em> output as a character string.
    * The platform default encoding is used to convert the underlying bytes to
    * characters.
    *
    * @return
    *    the collected <em>stdout</em> output as a character string,
    *    never <code>null</code>.
    */
   public String getOutString() {
      return _out.toString();
   }

   /**
    * Determines the number of bytes output to <em>stderr</em>.
    *
    * @return
    *    the number of bytes output to <em>stderr</em>.
    */
   public long getErrSize() {
      return getSize(_err);
   }

   /**
    * Copies all collected <em>stderr</em> output to the specified output
    * stream.
    *
    * @param target
    *    the {@link OutputStream} to copy the collected <em>stderr</em> output
    *    to, cannot be <code>null</code>.
    *
    * @throws IllegalArgumentException
    *    if <code>target == null</code>.
    *
    * @throws IOException
    *    in case of an I/O error.
    */
   public void writeErrTo(OutputStream target)
   throws IllegalArgumentException, IOException {
      copy(_err, target);
   }

   private void copy(OutputStream source, OutputStream target)
   throws IllegalArgumentException, IOException {

      // Check preconditions
      if (source == null) {
         throw new IllegalArgumentException("source == null");
      } else if (target == null) {
         throw new IllegalArgumentException("target == null");
      }

      // Handle in-memory and in-file streams differently
      if (_out instanceof ByteArrayOutputStream) {
         ((ByteArrayOutputStream) _out).writeTo(target);
      } else if (_out instanceof FileOutputStream) {
         throw new Error(); // TODO
      } else {
         throw new Error("Internal error: Unrecognized class " + _out.getClass().getName() + '.');
      }
   }

   /**
    * Retrieves all collected <em>stderr</em> output as a character string.
    * The platform default encoding is used to convert the underlying bytes to
    * characters.
    *
    * @return
    *    the collected <em>stderr</em> output as a character string,
    *    never <code>null</code>.
    */
   public String getErrString() {
      return _err.toString();
   }
}
