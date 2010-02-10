/*
* Copyright (c) 2008 Maxim Podkolzine (maxim.podkolzine@gmail.com)
* All rights reserved.
*
* Redistribution and use in source and binary forms, with or without
* modification, are permitted provided that the following conditions
* are met:
* 1. Redistributions of source code must retain the above copyright
*    notice, this list of conditions and the following disclaimer.
* 2. Redistributions in binary form must reproduce the above copyright
*    notice, this list of conditions and the following disclaimer in the
*    documentation and/or other materials provided with the distribution.
* 3. The name of the author may not be used to endorse or promote products
*    derived from this software without specific prior written permission.
*
* THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
* IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
* OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
* IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
* NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
* DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
* THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
* (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
* THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package jetbrains.buildServer.vcs.patches.fs;

import junit.framework.TestCase;

import java.util.Set;
import java.util.TreeSet;
import java.util.Collections;

import jetbrains.buildServer.vcs.patches.util.AssertionFailedException;

/**
 * @author Maxim Podkolzine (maxim.podkolzine@gmail.com)
 */
public class MemoryFileSystemImplTest extends TestCase {
  private MemoryFileSystemImpl fs;

  public void setUp() {
    fs = new MemoryFileSystemImpl();
  }

  /*************************************************************************************************
   * Tests for add() and contains().
   ************************************************************************************************/

  public void testAdd1() {
    fs.add("foo");
    assertTrue(fs.contains("foo"));
    assertFalse(fs.contains("foo/"));  // 'foo' is a file, not a directory
    checkFileSystem(new String[] { "foo" }, new String[] {});
  }

  public void testAdd2() {
    fs.add("foo/");
    assertFalse(fs.contains("foo"));  // 'foo' is a directory, not a file
    assertTrue(fs.contains("foo/"));
    checkFileSystem(new String[] {}, new String[] { "foo" });
  }

  public void testAdd3() {
    fs.add("foo/bar");
    assertTrue(fs.contains("foo/bar"));
    assertFalse(fs.contains("foo/bar/"));
    assertFalse(fs.contains("foo/"));  // there is a node 'foo', but fs does not contain 'foo/'
    checkFileSystem(new String[] { "foo/bar" }, new String[] {});
  }

  public void testAdd4() {
    fs.add("foo/bar");
    fs.add("foo/baz");
    assertTrue(fs.contains("foo/bar"));
    assertTrue(fs.contains("foo/baz"));
    assertFalse(fs.contains("foo/"));  // there is a node 'foo', but fs does not contain 'foo/'
    checkFileSystem(new String[] { "foo/bar", "foo/baz" }, new String[] {});
  }

  public void testAdd5() {
    fs.add("foo/");
    fs.add("foo/bar/");
    fs.add("foo/baz");
    assertTrue(fs.contains("foo/"));
    assertTrue(fs.contains("foo/bar/"));
    assertTrue(fs.contains("foo/baz"));
    checkFileSystem(new String[] { "foo/baz" }, new String[] { "foo", "foo/bar" });
  }

  public void testAdd6() {
    assertFalse(fs.add("foo"));
    assertTrue(fs.add("foo"));
    assertTrue(fs.add("foo/"));
    checkFileSystem(new String[] { "foo" }, new String[] {});
  }

  public void testAdd7() {
    assertFalse(fs.add("foo/bar"));
    assertTrue(fs.add("foo"));
    assertTrue(fs.add("foo/"));
    checkFileSystem(new String[] { "foo/bar" }, new String[] {});
  }

  public void testAdd8() {
    fs.add("foo");
    try {
      fs.add("foo/bar");  // 'foo' is a file, not a directory!
      fail();
    } catch (AssertionFailedException e) {}
  }

  /*************************************************************************************************
   * Tests for remove().
   ************************************************************************************************/

  public void testRemove1() {
    fs.add("foo");
    assertTrue(fs.remove("foo"));
    checkFileSystem(new String[] {}, new String[] {});
  }

  public void testRemove2() {
    fs.add("foo/");
    assertTrue(fs.remove("foo/"));
    checkFileSystem(new String[] {}, new String[] {});
  }

  public void testRemove3() {
    fs.add("foo");
    assertFalse(fs.remove("foo/"));  // 'foo' is a file, not a directory
    checkFileSystem(new String[] { "foo" }, new String[] {});
  }

  public void testRemove4() {
    fs.add("foo/");
    assertFalse(fs.remove("foo"));  // 'foo' is a directory, not a file
    checkFileSystem(new String[] {}, new String[] { "foo" });
  }

  public void testRemove5() {
    fs.add("foo/bar");
    assertFalse(fs.remove("foo"));  // 'foo' is a directory, not a file
    checkFileSystem(new String[] { "foo/bar" }, new String[] {});
  }

  public void testRemove6() {
    fs.add("foo/bar");
    assertTrue(fs.remove("foo/bar"));
    checkFileSystem(new String[] {}, new String[] {});
  }

  public void testRemove7() {
    fs.add("foo/");
    fs.add("foo/bar");
    assertTrue(fs.remove("foo/bar"));
    checkFileSystem(new String[] {}, new String[] { "foo" });
  }

  public void testRemove8() {
    fs.add("foo/bar");
    fs.add("foo/baz");
    assertTrue(fs.remove("foo/bar"));
    checkFileSystem(new String[] { "foo/baz" }, new String[] {});
  }

  public void testRemove9() {
    fs.add("foo/bar");
    assertTrue(fs.remove("foo/"));
    checkFileSystem(new String[] {}, new String[] {});
  }

  public void testRemove10() {
    fs.add("foo/bar/baz");
    assertTrue(fs.remove("foo/bar/"));
    assertFalse(fs.remove("foo/"));  // should've already been deleted
    checkFileSystem(new String[] {}, new String[] {});
  }

  public void testRemove11() {
    fs.add("foo/");
    fs.add("foo/bar/baz");
    assertTrue(fs.remove("foo/bar/"));
    assertTrue(fs.remove("foo/"));
    checkFileSystem(new String[] {}, new String[] {});
  }

  /*************************************************************************************************
   * Tests for containsAncestor() and containsNode().
   ************************************************************************************************/

  public void testContainsAncestor() {
    fs.add("foo/");
    fs.add("foo/bar/baz/");
    fs.add("foo/bar/baz/File");
    assertTrue(fs.containsAncestor("foo/bar/baz/File"));
    assertTrue(fs.containsAncestor("foo/bar/baz/AnotherFile"));
    assertTrue(fs.containsAncestor("foo/bar/baz/"));
    assertTrue(fs.containsAncestor("foo/bar/baz"));
    assertTrue(fs.containsAncestor("foo/bar/"));
    assertTrue(fs.containsAncestor("foo/bar"));
    assertFalse(fs.containsAncestor("foo/"));
    assertFalse(fs.containsAncestor("foo"));
  }

  public void testContainsNode() {
    fs.add("foo/bar");
    assertTrue(fs.containsNode("foo"));
    assertTrue(fs.containsNode("foo/"));
    assertTrue(fs.containsNode("foo/bar"));
    assertFalse(fs.containsNode("foo/baz"));
  }

  /*************************************************************************************************
   * Complex scenarios.
   ************************************************************************************************/

  public void testComplexScenario1() {
    fs.add("foo/bar/baz");
    fs.add("foo/bar/foo");
    fs.add("foobar");
    fs.remove("foo/");
    checkFileSystem(new String[] { "foobar" }, new String[] {});
  }

  public void testComplexScenario2() {
    fs.add("a/b/c/d");
    fs.add("b/a/b/c/d");
    fs.add("b/a/b/d/");
    fs.remove("b/a/b/c/");
    fs.add("b/a/b/c/e");
    fs.remove("a/");
    fs.add("a/b/");
    checkFileSystem(new String[] { "b/a/b/c/e" },
                    new String[] { "a/b", "b/a/b/d" });
  }

  public void testComplexScenario3() {
    fs.add("a/b/c");
    fs.add("a/b/d/");
    fs.add("a/b/d/e/f/");
    fs.add("a/b/d/e/f/g");
    fs.add("a/c/");
    fs.add("a/c/b");
    checkFileSystem(new String[] { "a/b/c", "a/b/d/e/f/g", "a/c/b" },
                    new String[] { "a/b/d", "a/b/d/e/f", "a/c" });
    fs.remove("a/");
    checkFileSystem(new String[] {}, new String[] {});
  }

  /*************************************************************************************************
   * Helper methods.
   ************************************************************************************************/

  private void checkFileSystem(String[] files, String[] directories) {
    Set<String> filesSet = new TreeSet<String>();
    Set<String> directoriesSet = new TreeSet<String>();
    fs.toCollections(filesSet, directoriesSet);

    Set<String> expectedFilesSet = new TreeSet<String>();
    Collections.addAll(expectedFilesSet, files);
    Set<String> expectedDirectoriesSet = new TreeSet<String>();
    Collections.addAll(expectedDirectoriesSet, directories);

    assertEquals(expectedFilesSet, filesSet);
    assertEquals(expectedDirectoriesSet, directoriesSet);
  }
}