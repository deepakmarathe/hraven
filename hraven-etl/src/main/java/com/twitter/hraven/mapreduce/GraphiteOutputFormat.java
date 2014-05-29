package com.twitter.hraven.mapreduce;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.Socket;
import java.net.URLEncoder;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.OutputCommitter;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.RecordWriter;
import org.apache.hadoop.mapreduce.TaskAttemptContext;

import com.twitter.hraven.Constants;
import com.twitter.hraven.HravenRecord;
import com.twitter.hraven.HravenService;
import com.twitter.hraven.JobHistoryRecordCollection;
import com.twitter.hraven.JobHistoryRecord;
import com.twitter.hraven.JobKey;
import com.twitter.hraven.RecordCategory;

/**
 * @author angad.singh {@link OutputFormat} for sending metrics to graphite
 */

public class GraphiteOutputFormat extends OutputFormat<HravenService, HravenRecord> {

  private static Log LOG = LogFactory.getLog(GraphiteOutputFormat.class);
  private static Writer writer;

  /**
   * {@link OutputCommitter} which does nothing
   */
  protected static class GraphiteOutputCommitter extends OutputCommitter {

    @Override
    public void setupJob(JobContext jobContext) throws IOException {
    }

    @Override
    public void setupTask(TaskAttemptContext taskContext) throws IOException {
    }

    @Override
    public boolean needsTaskCommit(TaskAttemptContext taskContext) throws IOException {
      return false;
    }

    @Override
    public void commitTask(TaskAttemptContext taskContext) throws IOException {
    }

    @Override
    public void abortTask(TaskAttemptContext taskContext) throws IOException {
    }

  }

  protected static class GraphiteRecordWriter extends RecordWriter<HravenService, HravenRecord> {

    private String METRIC_PREFIX;
    private int outputCount = 0;

    public GraphiteRecordWriter(String host, int port, String prefix) throws IOException {
      METRIC_PREFIX = prefix;

      try {
        // Open an connection to Graphite server.
        Socket socket = new Socket(host, port);
        writer = new OutputStreamWriter(socket.getOutputStream());
      } catch (Exception e) {
        throw new IOException("Error connecting to graphite, " + host + ":" + port, e);
      }
    }

    /**
     * Split a {@link JobHistoryRecordCollection} into {@link JobHistoryRecord}s and call the
     * {@link #writeRecord(HravenService, JobHistoryRecord)} method
     */

    @Override
    public void write(HravenService serviceKey, HravenRecord value) throws IOException,
        InterruptedException {
      JobHistoryRecordCollection recordCollection;

      if (value instanceof JobHistoryRecordCollection) {
        recordCollection = (JobHistoryRecordCollection) value;
      } else {
        recordCollection = new JobHistoryRecordCollection((JobHistoryRecord) value);
      }

      StringBuilder output = new StringBuilder();
      int lines = 0;

      try {
        GraphiteHistoryWriter graphiteWriter = new GraphiteHistoryWriter(METRIC_PREFIX, serviceKey, recordCollection, output);
        lines = graphiteWriter.write();
      } catch (Exception e) {
        LOG.error("Error generating metrics for graphite", e);
      }

      if (output.length() > 0) {
        
        try {
          LOG.info("SendToGraphite:" + recordCollection.getKey().toString() + lines + " lines");
          writer.write(output.toString());
        } catch (Exception e) {
          LOG.error("Error sending metrics to graphite", e);
          throw new IOException("Error sending metrics", e);
        }  
      }
    }

    @Override
    public void close(TaskAttemptContext context) throws IOException, InterruptedException {
      try {
        LOG.info("flushing records and closing writer");
        writer.close();
      } catch (Exception e) {
        throw new IOException("Error flush metrics to graphite", e);
      }
    }

  }

  @Override
  public void checkOutputSpecs(JobContext arg0) throws IOException, InterruptedException {
  }

  @Override
  public OutputCommitter getOutputCommitter(TaskAttemptContext context) throws IOException,
      InterruptedException {
    return new GraphiteOutputCommitter();
  }

  /**
   * Output a custom {@link GraphiteRecordWriter} to send metrics to graphite
   */
  @Override
  public RecordWriter<HravenService, HravenRecord> getRecordWriter(TaskAttemptContext context)
      throws IOException, InterruptedException {
    Configuration conf = context.getConfiguration();
    return new GraphiteRecordWriter(conf.get(Constants.JOBCONF_GRAPHITE_HOST_KEY,
      Constants.GRAPHITE_DEFAULT_HOST), conf.getInt(Constants.JOBCONF_GRAPHITE_PORT_KEY,
      Constants.GRAPHITE_DEFAULT_PORT), conf.get(Constants.JOBCONF_GRAPHITE_PREFIX,
      Constants.GRAPHITE_DEFAULT_PREFIX));
  }

}