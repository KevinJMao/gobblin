/*
 * Copyright (C) 2014-2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.runtime;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.collect.Maps;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import gobblin.metastore.FsStateStore;
import gobblin.util.JobLauncherUtils;


/**
 * Unit tests for {@link TaskStateCollectorService}.
 *
 * @author Yinan Li
 */
@Test(groups = { "gobblin.runtime" })
public class TaskStateCollectorServiceTest {

  private static final String JOB_NAME = "TestJob";
  private static final String JOB_ID = JobLauncherUtils.newJobId(JOB_NAME);
  private static final String TASK_ID_0 = JobLauncherUtils.newTaskId(JOB_ID, 0);
  private static final String TASK_ID_1 = JobLauncherUtils.newTaskId(JOB_ID, 1);

  private final Path outputTaskStateDir = new Path(TaskStateCollectorServiceTest.class.getSimpleName());

  private FileSystem localFs;

  private FsStateStore<TaskState> taskStateStore;

  private TaskStateCollectorService taskStateCollectorService;

  private final JobState jobState = new JobState();

  private final EventBus eventBus = new EventBus();

  private final Map<String, TaskState> taskStateMap = Maps.newHashMap();

  @BeforeClass
  public void setUp() throws Exception {
    this.localFs = FileSystem.getLocal(new Configuration());
    this.localFs.mkdirs(this.outputTaskStateDir);

    this.taskStateStore = new FsStateStore<>(this.localFs, this.outputTaskStateDir.toUri().getPath(), TaskState.class);

    this.taskStateCollectorService = new TaskStateCollectorService(new Properties(), this.jobState, this.eventBus,
        this.localFs, new Path(this.outputTaskStateDir, JOB_ID));

    this.eventBus.register(this);
  }

  @Test
  public void testPutIntoTaskStateStore() throws IOException {
    TaskState taskState1 = new TaskState();
    taskState1.setJobId(JOB_ID);
    taskState1.setTaskId(TASK_ID_0);
    this.taskStateStore.put(JOB_ID, TASK_ID_0 + AbstractJobLauncher.TASK_STATE_STORE_TABLE_SUFFIX, taskState1);

    TaskState taskState2 = new TaskState();
    taskState2.setJobId(JOB_ID);
    taskState2.setTaskId(TASK_ID_1);
    this.taskStateStore.put(JOB_ID, TASK_ID_1 + AbstractJobLauncher.TASK_STATE_STORE_TABLE_SUFFIX, taskState2);
  }

  @Test(dependsOnMethods = "testPutIntoTaskStateStore")
  public void testCollectOutputTaskStates() throws Exception {
    this.taskStateCollectorService.runOneIteration();
    Assert.assertEquals(this.jobState.getTaskStates().size(), 2);
    Assert.assertEquals(this.taskStateMap.size(), 2);
    Assert.assertEquals(this.taskStateMap.get(TASK_ID_0).getJobId(), JOB_ID);
    Assert.assertEquals(this.taskStateMap.get(TASK_ID_0).getTaskId(), TASK_ID_0);
    Assert.assertEquals(this.taskStateMap.get(TASK_ID_1).getJobId(), JOB_ID);
    Assert.assertEquals(this.taskStateMap.get(TASK_ID_1).getTaskId(), TASK_ID_1);
  }

  @AfterClass
  public void tearDown() throws IOException {
    if (this.localFs.exists(this.outputTaskStateDir)) {
      this.localFs.delete(this.outputTaskStateDir, true);
    }
  }

  @Subscribe
  @Test(enabled = false)
  public void handleNewOutputTaskStateEvent(NewTaskCompletionEvent newOutputTaskStateEvent) {
    for (TaskState taskState : newOutputTaskStateEvent.getTaskStates()) {
      this.taskStateMap.put(taskState.getTaskId(), taskState);
    }
  }
}
