/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.buildTriggers.vcs.vault;

import jetbrains.buildServer.vcs.patches.PatchTestCase;
import jetbrains.buildServer.vcs.patches.PatchBuilderImpl;
import jetbrains.buildServer.vcs.impl.SVcsRootImpl;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.util.FileUtil;

import java.io.File;
import java.io.ByteArrayOutputStream;

import org.testng.annotations.*;
import org.jetbrains.annotations.NotNull;
import VaultClientIntegrationLib.ServerOperations;
import VaultClientIntegrationLib.DateSortOption;
import VaultLib.VaultHistoryItem;

/**
 * User: vbedrosova
 * Date: 20.07.2009
 * Time: 18:29:44
 */

public class VaultPatchBuilderTest extends PatchTestCase {
  private static final String SERVER_URL = "http://ruspd-pc02.swiftteams.local:8888";
  private static final String USER = "admin";
  private static final String PASWORD = "password";

  private long myBeginTx;

  protected String getTestDataPath() {
    return "vault-tests" + File.separator + "testData";
  }

  private String getObjectPathForRepo(@NotNull String path) {
    return getTestDataPath() + File.separator + "repoContent" + File.separator + path;
  }

  private File getBeforeFolder() {
    return getTestData("before");
  }

  private File getAfterFolder() {
    return getTestData("after");
  }

  @BeforeSuite
  protected void setUpSuite() throws Exception {
    ServerOperations.client.LoginOptions.URL = SERVER_URL;
    ServerOperations.client.LoginOptions.User = USER;
    ServerOperations.client.LoginOptions.Password = PASWORD;
    ServerOperations.client.AutoCommit = true;

    final File testDataSvn = TestUtil.getTestData("repoContent_Vcs", null);
    final File testDataNoSvn = new File(testDataSvn.getAbsolutePath().replace("_Vcs", ""));
    FileUtil.createDir(testDataNoSvn);
    FileUtil.copyDir(testDataSvn, testDataNoSvn, false);
    FileUtil.createDir(new File(testDataNoSvn, "fold1"));
    FileUtil.createDir(new File(testDataNoSvn, "fold2"));
  }

  private long getBeginTx() throws Exception {
    final VaultHistoryItem[] historyItems = ServerOperations.ProcessCommandHistory("$", true, DateSortOption.desc, null, null, null, null, null, null, -1, -1, 1);
    return historyItems[0].get_TxID();
  }

  @BeforeMethod
  protected void setUp() throws Exception {
//    final File cache = FileUtil.createTempDirectory("vault", "");
//    FileUtil.delete(cache);
//    System.setProperty("vault.caches.path", cache.getAbsolutePath());
    super.setUp();

    Thread.sleep(2000);

    final String testName = getTestName();
    final File testDataSvn = TestUtil.getTestDataMayNotExist(testName + "_Vcs", null);
    final File testDataNoSvn = new File(testDataSvn.getAbsolutePath().replace("_Vcs", ""));
    FileUtil.createDir(testDataNoSvn);
    if (testDataSvn.isDirectory()) {
      FileUtil.copyDir(testDataSvn, testDataNoSvn, false);
    } else {
      FileUtil.createDir(testDataNoSvn);
    }
    FileUtil.createDir(new File(testDataNoSvn, "before"));
    FileUtil.createDir(new File(testDataNoSvn, "after"));

    ServerOperations.client.LoginOptions.Repository = testName;

    try {
      ServerOperations.ProcessCommandDeleteRepository(testName);
    } catch (Exception e) {
      System.out.println("Unable to delete repository " + testName + "( may be doesn't exist )");
    }
    ServerOperations.ProcessCommandAddRepository(testName, false);


    ServerOperations.Login();
    myBeginTx = getBeginTx();
    ServerOperations.Logout();
  }

  @AfterMethod
  protected void tearDown() throws Exception {
    Thread.sleep(2000);    
    ServerOperations.ProcessCommandDeleteRepository(getTestName());
  }

  private void runTest(String fromVersion, @NotNull String toVersion) throws Exception {
    final SVcsRootImpl root = new SVcsRootImpl("vault");    
    root.addProperty("vault.server", SERVER_URL);
    root.addProperty("vault.user", USER);
    root.addProperty("secure:vault.password", PASWORD);
    root.addProperty("vault.repo", getTestName());

    final ByteArrayOutputStream outputBuffer = new ByteArrayOutputStream();
    final PatchBuilderImpl patchBuilder = new PatchBuilderImpl(outputBuffer);
    final VaultPatchBuilder vaultPatchBuilder = new VaultPatchBuilder(root, fromVersion, toVersion);

    try {
      vaultPatchBuilder.buildPatch(patchBuilder, IncludeRule.createDefaultInstance());
    } finally {
      patchBuilder.close();
    }
    checkPatchResult(outputBuffer.toByteArray());
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testEmptyRepoCleanPatch() throws Exception {
    runTest(null, "" + myBeginTx);
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testEmptyRepoFromEqualsTo() throws Exception {
    runTest("" + myBeginTx, "" + myBeginTx);
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testEmptyRepoToDoesNotExist() throws Exception {
    runTest("" + myBeginTx, "" + (myBeginTx + 20));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testExportOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.Logout();
    runTest(null, "" + (myBeginTx + 1));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testExportOneDir() throws Exception {
    FileUtil.createDir(new File(getAfterFolder(), "fold1"));

    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.Logout();
    runTest(null, "" + (myBeginTx + 1));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testRenameOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/file1", "new_file1");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testRenameOneEmptyDir() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold1"));
    FileUtil.createDir(new File(getAfterFolder(), "new_fold1"));

    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/fold1", "new_fold1");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testDeleteOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    final String[] toDelete = {"$/file1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testDeleteOneEmptyDir() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold1"));

    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    final String[] toDelete = {"$/fold1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddAndRenameOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/file1", "new_file1");
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddAndRenameOneEmptyDir() throws Exception {
    FileUtil.createDir(new File(getAfterFolder(), "new_fold1"));

    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/fold1", "new_fold1");
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddAndDeleteOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    final String[] toDelete = {"$/file1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddAndDeleteOneEmptyDir() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    final String[] toDelete = {"$/fold1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddRenameAndDeleteOneTextFile() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/file1", "new_file1");
    final String[] toDelete = {"$/new_file1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 3));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddRenameAndDeleteOneEmptyDir() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandRename("$/fold1", "new_fold1");
    final String[] toDelete = {"$/new_fold1"};
    ServerOperations.ProcessCommandDelete(toDelete);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 3));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddFileIntoFolders() throws Exception {
    final String[] toAdd = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$/fold1/fold2", toAdd);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 1));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddFilesIntoFolders() throws Exception {
    final String[] toAdd1 = {getObjectPathForRepo("file1")};
    final String[] toAdd2 = {getObjectPathForRepo("file2")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$/fold1/fold2", toAdd1);
    ServerOperations.ProcessCommandAdd("$/fold1", toAdd2);
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testRenameFolder() throws Exception {
    final String[] toAdd1 = {getObjectPathForRepo("file1")};
    final String[] toAdd2 = {getObjectPathForRepo("file2")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$/fold1/fold2", toAdd1);
    ServerOperations.ProcessCommandAdd("$/fold1", toAdd2);
    ServerOperations.ProcessCommandRename("$/fold1", "new_fold1");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 2), "" + (myBeginTx + 3));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddFilesIntoFoldersRenameFolder() throws Exception {
    final String[] toAdd1 = {getObjectPathForRepo("file1")};
    final String[] toAdd2 = {getObjectPathForRepo("file2")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$/fold1/fold2", toAdd1);
    ServerOperations.ProcessCommandAdd("$/fold1", toAdd2);
    ServerOperations.ProcessCommandRename("$/fold1", "new_fold1");
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 3));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testMoveFile() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold1"));

    final String[] toAdd = {getObjectPathForRepo("file1"), getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandMove("$/file1", "$/fold1");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testAddFileAndMove() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold1"));

    final String[] toAdd = {getObjectPathForRepo("file1"), getObjectPathForRepo("fold1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandMove("$/file1", "$/fold1");
    ServerOperations.Logout();
    runTest("" + myBeginTx, "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testMoveFolder() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold1"));
    FileUtil.createDir(new File(getBeforeFolder(), "fold2"));
    final File fold1 = new File(getAfterFolder(), "fold1");
    FileUtil.createDir(fold1);
    FileUtil.createDir(new File(fold1, "fold2"));

    final String[] toAdd = {getObjectPathForRepo("fold1"), getObjectPathForRepo("fold2")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$", toAdd);
    ServerOperations.ProcessCommandMove("$/fold2", "$/fold1");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 1), "" + (myBeginTx + 2));
  }

  @Test(groups = {"all", "vault"}, dataProvider = "dp")
  public void testMoveFolderWithContent() throws Exception {
    FileUtil.createDir(new File(getBeforeFolder(), "fold2"));

    final String[] toAdd1 = {getObjectPathForRepo("file2")};
    final String[] toAdd2 = {getObjectPathForRepo("file1")};
    ServerOperations.Login();
    ServerOperations.ProcessCommandAdd("$/fold1/fold3", toAdd1);
    ServerOperations.ProcessCommandAdd("$/fold1", toAdd2);
    ServerOperations.ProcessCommandCreateFolder("$/fold2");
    ServerOperations.ProcessCommandMove("$/fold1", "$/fold2");
    ServerOperations.Logout();
    runTest("" + (myBeginTx + 3), "" + (myBeginTx + 4));
  }

//  @Test(groups = {"all", "vault"}, dataProvider = "dp")
//  public void testAddFileAndShare() throws Exception {
//    final String[] toAdd1 = {getObjectPathForRepo("file1")};
//    ServerOperations.Login();
//    ServerOperations.ProcessCommandAdd("$/fold1", toAdd1);
//    ServerOperations.ProcessCommandShare("$/fold1/file1", "$");
//    ServerOperations.Logout();
//    runTest("" + myBeginTx, "" + (myBeginTx + 2));
//  }
//  @Test(groups = {"all", "vault"}, dataProvider = "dp")
//  public void testChangeOneTextFile() throws Exception {
//    runTest("27.08.2009 11:54:15", "27.08.2009 12:12:29");
//  }
}
