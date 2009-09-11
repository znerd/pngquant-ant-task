// Copyright 2007-2009, PensioenPage B.V.
package com.pensioenpage.jynx.pngquant;

import java.io.ByteArrayOutputStream;
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
      _outBuffer = new ByteArrayOutputStream();
      _errBuffer = new ByteArrayOutputStream();
   }


   //-------------------------------------------------------------------------
   // Fields
   //-------------------------------------------------------------------------

   /**
    * The buffer holding all <em>stdout</em> output. Never <code>null</code>.
    */
   private final ByteArrayOutputStream _outBuffer;

   /**
    * The buffer holding all <em>stderr</em> output. Never <code>null</code>.
    */
   private final ByteArrayOutputStream _errBuffer;

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

   // Specified by ExecuteStreamHandler
   public void setProcessInputStream(OutputStream os) {
      // ignore, we don't send input to the process
   }

   // Specified by ExecuteStreamHandler
   public void setProcessOutputStream(InputStream is) {
      _outThread = new Thread(new StreamPumper(is, _outBuffer));
   }

   // Specified by ExecuteStreamHandler
   public void setProcessErrorStream(InputStream is) {
      _errThread = new Thread(new StreamPumper(is, _errBuffer));
   }

   // Specified by ExecuteStreamHandler
   public void start() {
      _outThread.start();
      _errThread.start();
   }

   // Specified by ExecuteStreamHandler
   public void stop() {
      try {
         _outThread.join();
      } catch (InterruptedException e) {
         // ignore
      }
      try {
         _errThread.join();
      } catch (InterruptedException e) {
         // ignore
      }
   }

   /**
    * Copied all collected <em>stdout</em> output to the specified output
    * stream.
    *
    * @param os
    *    the {@link OutputStream} to copy the collected <em>stdout</em> output
    *    to, cannot be <code>null</code>.
    *
    * @throws IllegalArgumentException
    *    if <code>os == null</code>.
    *
    * @throws IOException
    *    in case of an I/O error.
    */
   public void writeOutTo(OutputStream os)
   throws IOException {
      if (os == null) {
         throw new IllegalArgumentException("os == null");
      }
      _outBuffer.writeTo(os);
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
      return _outBuffer.toString();
   }

   /**
    * Copied all collected <em>stderr</em> output to the specified output
    * stream.
    *
    * @param os
    *    the {@link OutputStream} to copy the collected <em>stderr</em> output
    *    to, cannot be <code>null</code>.
    *
    * @throws IllegalArgumentException
    *    if <code>os == null</code>.
    *
    * @throws IOException
    *    in case of an I/O error.
    */
   public void writeErrTo(OutputStream os) throws IOException {
      _errBuffer.writeTo(os);
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
      return _errBuffer.toString();
   }
}
