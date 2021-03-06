package com.intellij.openapi.diff.impl.processing;

import com.intellij.openapi.diff.ex.DiffFragment;
import com.intellij.openapi.diff.impl.ComparisonPolicy;
import com.intellij.openapi.diff.impl.highlighting.FragmentSide;
import com.intellij.openapi.diff.impl.highlighting.Util;
import junit.framework.Assert;
import junit.framework.TestCase;

public class LineBlocksDiffPolicyTest extends TestCase{
  public void test() {
    DiffPolicy.LineBlocks diffPolicy = new DiffPolicy.LineBlocks(ComparisonPolicy.DEFAULT);
    checkPolicy(diffPolicy, "abc\n123\n", "ABC\nXYZ\n");
    checkPolicy(diffPolicy, "abc\n123", "ABC\nXYZ");
    checkPolicy(diffPolicy, "abc\n123\n", "ABC\nXYZ");
  }

  private void checkPolicy(DiffPolicy.LineBlocks diffPolicy, String text1, String text2) {
    DiffFragment[] fragments = diffPolicy.buildFragments(text1, text2);
    Assert.assertEquals(text1, Util.getText(fragments, FragmentSide.SIDE1));
    assertEquals(text2, Util.getText(fragments, FragmentSide.SIDE2));
  }
}
