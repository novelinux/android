/*
 * Copyright 2002,2004 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.compress.tar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The TarBuffer class implements the tar archive concept of a buffered input
 * stream. This concept goes back to the days of blocked tape drives and special
 * io devices. In the Java universe, the only real function that this class
 * performs is to ensure that files have the correct "block" size, or other tars
 * will complain. <p>
 *
 * You should never have a need to access this class directly. TarBuffers are
 * created by Tar IO Streams.
 *
 * @author <a href="mailto:time@ice.com">Timothy Gerard Endres</a>
 * @author <a href="mailto:peter@apache.org">Peter Donald</a>
 * @version $Revision: 1.1 $ $Date: 2005/01/20 23:19:14 $
 */
class TarBuffer
{
    public static final int DEFAULT_RECORDSIZE = ( 512 );
    public static final int DEFAULT_BLOCKSIZE = ( DEFAULT_RECORDSIZE * 20 );

    private byte[] m_blockBuffer;
    private int m_blockSize;
    private int m_currBlkIdx;
    private int m_currRecIdx;
    private boolean m_debug;

    private InputStream m_input;
    private OutputStream m_output;
    private int m_recordSize;
    private int m_recsPerBlock;

    public TarBuffer( final InputStream input )
    {
        this( input, TarBuffer.DEFAULT_BLOCKSIZE );
    }

    public TarBuffer( final InputStream input, final int blockSize )
    {
        this( input, blockSize, TarBuffer.DEFAULT_RECORDSIZE );
    }

    public TarBuffer( final InputStream input,
                      final int blockSize,
                      final int recordSize )
    {
        m_input = input;
        initialize( blockSize, recordSize );
    }

    public TarBuffer( final OutputStream output )
    {
        this( output, TarBuffer.DEFAULT_BLOCKSIZE );
    }

    public TarBuffer( final OutputStream output, final int blockSize )
    {
        this( output, blockSize, TarBuffer.DEFAULT_RECORDSIZE );
    }

    public TarBuffer( final OutputStream output,
                      final int blockSize,
                      final int recordSize )
    {
        m_output = output;
        initialize( blockSize, recordSize );
    }

    /**
     * Set the debugging flag for the buffer.
     *
     * @param debug If true, print debugging output.
     */
    public void setDebug( final boolean debug )
    {
        m_debug = debug;
    }

    /**
     * Get the TAR Buffer's block size. Blocks consist of multiple records.
     *
     * @return The BlockSize value
     */
    public int getBlockSize()
    {
        return m_blockSize;
    }

    /**
     * Get the current block number, zero based.
     *
     * @return The current zero based block number.
     */
    public int getCurrentBlockNum()
    {
        return m_currBlkIdx;
    }

    /**
     * Get the current record number, within the current block, zero based.
     * Thus, current offset = (currentBlockNum * recsPerBlk) + currentRecNum.
     *
     * @return The current zero based record number.
     */
    public int getCurrentRecordNum()
    {
        return m_currRecIdx - 1;
    }

    /**
     * Get the TAR Buffer's record size.
     *
     * @return The RecordSize value
     */
    public int getRecordSize()
    {
        return m_recordSize;
    }

    /**
     * Determine if an archive record indicate End of Archive. End of archive is
     * indicated by a record that consists entirely of null bytes.
     *
     * @param record The record data to check.
     * @return The EOFRecord value
     */
    public boolean isEOFRecord( final byte[] record )
    {
        final int size = getRecordSize();
        for( int i = 0; i < size; ++i )
        {
            if( record[ i ] != 0 )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Close the TarBuffer. If this is an output buffer, also flush the current
     * block before closing.
     */
    public void close()
        throws IOException
    {
        if( m_debug )
        {
            debug( "TarBuffer.closeBuffer()." );
        }

        if( null != m_output )
        {
            flushBlock();

            if( m_output != System.out && m_output != System.err )
            {
                m_output.close();
                m_output = null;
            }
        }
        else if( m_input != null )
        {
            if( m_input != System.in )
            {
                m_input.close();
                m_input = null;
            }
        }
    }

    /**
     * Read a record from the input stream and return the data.
     *
     * @return The record data.
     * @exception IOException Description of Exception
     */
    public byte[] readRecord()
        throws IOException
    {
        if( m_debug )
        {
            final String message = "ReadRecord: recIdx = " + m_currRecIdx +
                " blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_input )
        {
            final String message = "reading from an output buffer";
            throw new IOException( message );
        }

        if( m_currRecIdx >= m_recsPerBlock )
        {
            if( !readBlock() )
            {
                return null;
            }
        }

        final byte[] result = new byte[ m_recordSize ];
        System.arraycopy( m_blockBuffer,
                          ( m_currRecIdx * m_recordSize ),
                          result,
                          0,
                          m_recordSize );

        m_currRecIdx++;

        return result;
    }

    /**
     * Skip over a record on the input stream.
     */
    public void skipRecord()
        throws IOException
    {
        if( m_debug )
        {
            final String message = "SkipRecord: recIdx = " + m_currRecIdx +
                " blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_input )
        {
            final String message = "reading (via skip) from an output buffer";
            throw new IOException( message );
        }

        if( m_currRecIdx >= m_recsPerBlock )
        {
            if( !readBlock() )
            {
                return;// UNDONE
            }
        }

        m_currRecIdx++;
    }

    /**
     * Write an archive record to the archive.
     *
     * @param record The record data to write to the archive.
     */
    public void writeRecord( final byte[] record )
        throws IOException
    {
        if( m_debug )
        {
            final String message = "WriteRecord: recIdx = " + m_currRecIdx +
                " blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_output )
        {
            final String message = "writing to an input buffer";
            throw new IOException( message );
        }

        if( record.length != m_recordSize )
        {
            final String message = "record to write has length '" +
                record.length + "' which is not the record size of '" +
                m_recordSize + "'";
            throw new IOException( message );
        }

        if( m_currRecIdx >= m_recsPerBlock )
        {
            writeBlock();
        }

        System.arraycopy( record,
                          0,
                          m_blockBuffer,
                          ( m_currRecIdx * m_recordSize ),
                          m_recordSize );

        m_currRecIdx++;
    }

    /**
     * Write an archive record to the archive, where the record may be inside of
     * a larger array buffer. The buffer must be "offset plus record size" long.
     *
     * @param buffer The buffer containing the record data to write.
     * @param offset The offset of the record data within buf.
     */
    public void writeRecord( final byte[] buffer, final int offset )
        throws IOException
    {
        if( m_debug )
        {
            final String message = "WriteRecord: recIdx = " + m_currRecIdx +
                " blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_output )
        {
            final String message = "writing to an input buffer";
            throw new IOException( message );
        }

        if( ( offset + m_recordSize ) > buffer.length )
        {
            final String message = "record has length '" + buffer.length +
                "' with offset '" + offset + "' which is less than the record size of '" +
                m_recordSize + "'";
            throw new IOException( message );
        }

        if( m_currRecIdx >= m_recsPerBlock )
        {
            writeBlock();
        }

        System.arraycopy( buffer,
                          offset,
                          m_blockBuffer,
                          ( m_currRecIdx * m_recordSize ),
                          m_recordSize );

        m_currRecIdx++;
    }

    /**
     * Flush the current data block if it has any data in it.
     */
    private void flushBlock()
        throws IOException
    {
        if( m_debug )
        {
            final String message = "TarBuffer.flushBlock() called.";
            debug( message );
        }

        if( m_output == null )
        {
            final String message = "writing to an input buffer";
            throw new IOException( message );
        }

        if( m_currRecIdx > 0 )
        {
            writeBlock();
        }
    }

    /**
     * Initialization common to all constructors.
     */
    private void initialize( final int blockSize, final int recordSize )
    {
        m_debug = false;
        m_blockSize = blockSize;
        m_recordSize = recordSize;
        m_recsPerBlock = ( m_blockSize / m_recordSize );
        m_blockBuffer = new byte[ m_blockSize ];

        if( null != m_input )
        {
            m_currBlkIdx = -1;
            m_currRecIdx = m_recsPerBlock;
        }
        else
        {
            m_currBlkIdx = 0;
            m_currRecIdx = 0;
        }
    }

    /**
     * @return false if End-Of-File, else true
     */
    private boolean readBlock()
        throws IOException
    {
        if( m_debug )
        {
            final String message = "ReadBlock: blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_input )
        {
            final String message = "reading from an output buffer";
            throw new IOException( message );
        }

        m_currRecIdx = 0;

        int offset = 0;
        int bytesNeeded = m_blockSize;

        while( bytesNeeded > 0 )
        {
            final long numBytes = m_input.read( m_blockBuffer, offset, bytesNeeded );

            //
            // NOTE
            // We have fit EOF, and the block is not full!
            //
            // This is a broken archive. It does not follow the standard
            // blocking algorithm. However, because we are generous, and
            // it requires little effort, we will simply ignore the error
            // and continue as if the entire block were read. This does
            // not appear to break anything upstream. We used to return
            // false in this case.
            //
            // Thanks to 'Yohann.Roussel@alcatel.fr' for this fix.
            //
            if( numBytes == -1 )
            {
                break;
            }

            offset += numBytes;
            bytesNeeded -= numBytes;

            if( numBytes != m_blockSize )
            {
                if( m_debug )
                {
                    System.err.println( "ReadBlock: INCOMPLETE READ "
                                        + numBytes + " of " + m_blockSize
                                        + " bytes read." );
                }
            }
        }

        m_currBlkIdx++;

        return true;
    }

    /**
     * Write a TarBuffer block to the archive.
     *
     * @exception IOException Description of Exception
     */
    private void writeBlock()
        throws IOException
    {
        if( m_debug )
        {
            final String message = "WriteBlock: blkIdx = " + m_currBlkIdx;
            debug( message );
        }

        if( null == m_output )
        {
            final String message = "writing to an input buffer";
            throw new IOException( message );
        }

        m_output.write( m_blockBuffer, 0, m_blockSize );
        m_output.flush();

        m_currRecIdx = 0;
        m_currBlkIdx++;
    }

    protected void debug( final String message )
    {
        if( m_debug )
        {
            System.err.println( message );
        }
    }
}
