/*
Copyright 2014 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.hraven.etl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestFileLister {
  private static HBaseTestingUtility UTIL;

  @BeforeClass
  public static void setupBeforeClass() throws Exception {
    UTIL = new HBaseTestingUtility();
    UTIL.startMiniCluster();
  }

  @Test
  public void testMoveFileHdfs() throws IOException {
    Path src = new Path("/dir1/file1234.txt");
    Path dest = new Path("/dir3/dir00004");
    FileSystem hdfs = FileSystem.get(UTIL.getConfiguration());
    boolean os = hdfs.createNewFile(src);
    assertTrue(os);
    os = hdfs.mkdirs(dest);
    assertTrue(os);
    FileLister.moveFileHdfs(hdfs, src, dest);
    String destFullPathStr = dest.toUri() + "/" + src.getName();
    Path expFile = new Path(destFullPathStr);
    assertTrue(hdfs.exists(expFile));
    assertFalse(hdfs.exists(src));
  }

  @Test
  public void testMoveFileHdfsNullDest() throws IOException {
    Path src = new Path("/dir1/dir12345");
    FileSystem hdfs = FileSystem.get(UTIL.getConfiguration());
    boolean os = hdfs.createNewFile(src);
    assertTrue(os);
    FileLister.moveFileHdfs(hdfs, src, null);
    assertTrue(hdfs.exists(src));
  }

  @Test
  public void testGetDatedRootNull() {
    assertNull(FileLister.getDatedRoot(null));
  }

  @Test
  public void testGetDatedRoot() {
    String root = "abc";

    DateFormat df = new SimpleDateFormat("yyyy/MM/dd");
    String formatted = df.format(new Date());
    String expRoot = root + "/" + formatted;
    String actualRoot = FileLister.getDatedRoot(root);
    assertNotNull(actualRoot);
    assertEquals(actualRoot, expRoot);
  }

  @Test
  public void testPruneFileListBySize() throws IOException {

    long maxFileSize = 20L;
    FileStatus[] origList = new FileStatus[2];
    FileSystem hdfs = FileSystem.get(UTIL.getConfiguration());
    Path inputPath = new Path("/inputdir_filesize");
    boolean os = hdfs.mkdirs(inputPath);
    assertTrue(os);
    assertTrue(hdfs.exists(inputPath));

    final String JOB_HISTORY_FILE_NAME =
        "src/test/resources/job_1329348432655_0001-1329348443227-user-Sleep+job-1329348468601-10-1-SUCCEEDED-default.jhist";
    File jobHistoryfile = new File(JOB_HISTORY_FILE_NAME);
    Path srcPath = new Path(jobHistoryfile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputPath);
    Path expPath = new Path(inputPath.toUri() + "/" + srcPath.getName());
    assertTrue(hdfs.exists(expPath));
    origList[0] = hdfs.getFileStatus(expPath);

    final String JOB_CONF_FILE_NAME =
        "src/test/resources/job_1329348432655_0001_conf.xml";
    File jobConfFile = new File(JOB_CONF_FILE_NAME);
    srcPath = new Path(jobConfFile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputPath);
    expPath = new Path(inputPath.toUri() + "/" + srcPath.getName());
    assertTrue(hdfs.exists(expPath));
    origList[1] = hdfs.getFileStatus(expPath);

    Path relocationPath = new Path("/relocation_filesize");
    os = hdfs.mkdirs(relocationPath);
    assertTrue(os);
    assertTrue(hdfs.exists(relocationPath));

    FileStatus [] prunedList = FileLister.pruneFileListBySize(maxFileSize, origList, hdfs, inputPath, relocationPath);
    assertNotNull(prunedList);
    assertTrue(prunedList.length == 0);

    Path emptyFile = new Path(inputPath.toUri() + "/" + "job_1329341111111_0101-1329111113227-user2-Sleep.jhist");
    os = hdfs.createNewFile(emptyFile);
    assertTrue(os);
    assertTrue(hdfs.exists(emptyFile));
    origList[0] = hdfs.getFileStatus(emptyFile);

    Path emptyConfFile = new Path(inputPath.toUri() + "/" + "job_1329341111111_0101_conf.xml");
    os = hdfs.createNewFile(emptyConfFile);

    assertTrue(os);
    assertTrue(hdfs.exists(emptyConfFile));
    origList[1] = hdfs.getFileStatus(emptyConfFile);

    prunedList = FileLister.pruneFileListBySize(maxFileSize, origList, hdfs, inputPath, relocationPath);
    assertNotNull(prunedList);
    assertTrue(prunedList.length == 2);

  }

  @Test
  public void testPruneFileListMultipleFilesAlreadyMovedCases() throws IOException {

    long maxFileSize = 20L;
    FileStatus[] origList = new FileStatus[10];
    FileSystem hdfs = FileSystem.get(UTIL.getConfiguration());
    Path inputPath = new Path("/inputdir_filesize_multiple");
    boolean os = hdfs.mkdirs(inputPath);
    assertTrue(os);
    assertTrue(hdfs.exists(inputPath));

    Path relocationPath = new Path("/relocation_filesize_multiple");
    os = hdfs.mkdirs(relocationPath);
    assertTrue(os);
    assertTrue(hdfs.exists(relocationPath));

    Path emptyFile = new Path(inputPath.toUri() + "/" + "job_1329341111111_0101-1329111113227-user2-Sleep.jhist");
    os = hdfs.createNewFile(emptyFile);
    assertTrue(os);
    assertTrue(hdfs.exists(emptyFile));
    origList[0] = hdfs.getFileStatus(emptyFile);

    Path emptyConfFile = new Path(inputPath.toUri() + "/" + "job_1329341111111_0101_conf.xml");
    os = hdfs.createNewFile(emptyConfFile);

    assertTrue(os);
    assertTrue(hdfs.exists(emptyConfFile));
    origList[1] = hdfs.getFileStatus(emptyConfFile);

    final String JOB_HISTORY_FILE_NAME =
        "src/test/resources/job_1329348432655_0001-1329348443227-user-Sleep+job-1329348468601-10-1-SUCCEEDED-default.jhist";
    File jobHistoryfile = new File(JOB_HISTORY_FILE_NAME);
    Path srcPath = new Path(jobHistoryfile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputPath);
    Path expPath = new Path(inputPath.toUri() + "/" + srcPath.getName());
    assertTrue(hdfs.exists(expPath));
    origList[2] = hdfs.getFileStatus(expPath);

    final String JOB_CONF_FILE_NAME =
        "src/test/resources/job_1329348432655_0001_conf.xml";
    File jobConfFile = new File(JOB_CONF_FILE_NAME);
    srcPath = new Path(jobConfFile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputPath);
    expPath = new Path(inputPath.toUri() + "/" + srcPath.getName());
    assertTrue(hdfs.exists(expPath));
    origList[3] = hdfs.getFileStatus(expPath);

    Path inputPath2 = new Path(inputPath.toUri() + "/" + "job_1311222222255_0221-1311111143227-user10101-WordCount-1-SUCCEEDED-default.jhist");
    hdfs.copyFromLocalFile(srcPath, inputPath2);
    assertTrue(hdfs.exists(inputPath2));
    origList[4] = hdfs.getFileStatus(inputPath2);

    Path inputPath3 = new Path(inputPath.toUri() + "/" + "job_1399999999155_0991-1311111143227-user3321-TeraGen-1-SUCCEEDED-default.jhist");
    hdfs.copyFromLocalFile(srcPath, inputPath3);
    assertTrue(hdfs.exists(inputPath3));
    origList[5] = hdfs.getFileStatus( inputPath3);

    Path emptyFile2 = new Path(inputPath.toUri() + "/" + "job_1329343333333_5551-1329111113227-user2-SomethingElse.jhist");
    os = hdfs.createNewFile(emptyFile2);
    assertTrue(os);
    assertTrue(hdfs.exists(emptyFile2));
    origList[6] = hdfs.getFileStatus(emptyFile2);

    Path emptyConfFile2 = new Path(inputPath.toUri() + "/" + "job_1329343333333_5551_conf.xml");
    os = hdfs.createNewFile(emptyConfFile2);
    assertTrue(os);
    assertTrue(hdfs.exists(emptyConfFile2));
    origList[7] = hdfs.getFileStatus(emptyConfFile2);

    Path inputConfPath2 = new Path(inputPath.toUri() + "/" + "job_1311222222255_0221_conf.xml");
    srcPath = new Path(jobConfFile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputConfPath2);
    assertTrue(hdfs.exists(inputConfPath2));
    origList[8] = hdfs.getFileStatus(inputConfPath2);

    Path inputConfPath3 = new Path(inputPath.toUri() + "/" + "job_1399999999155_0991_conf.xml");
    srcPath = new Path(jobConfFile.toURI());
    hdfs.copyFromLocalFile(srcPath, inputConfPath3);
    assertTrue(hdfs.exists(inputConfPath2));
    origList[9] = hdfs.getFileStatus(inputConfPath3);

    FileStatus [] prunedList = FileLister.pruneFileListBySize(maxFileSize, origList, hdfs, inputPath, relocationPath);
    assertNotNull(prunedList);
    assertTrue(prunedList.length == 4);
  }

  @AfterClass
  public static void tearDownAfterClass() throws Exception {
    UTIL.shutdownMiniCluster();
  }
}