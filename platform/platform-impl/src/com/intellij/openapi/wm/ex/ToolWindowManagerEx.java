// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.ex;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.DesktopLayout;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public abstract class ToolWindowManagerEx extends ToolWindowManager {
  /**
   * @deprecated Use {{@link #registerToolWindow(RegisterToolWindowTask)}}
   */
  @Deprecated
  public abstract void initToolWindow(@NotNull ToolWindowEP bean);

  @ApiStatus.Internal
  public abstract void init(ProjectFrameHelper frameHelper);

  @NotNull
  public static ToolWindowManagerEx getInstanceEx(@NotNull Project project) {
    return (ToolWindowManagerEx)getInstance(project);
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  public void addToolWindowManagerListener(@NotNull ToolWindowManagerListener listener, @NotNull Disposable parentDisposable) {
  }

  /**
   * @deprecated Use {@link ToolWindowManagerListener#TOPIC}
   */
  @Deprecated
  public void removeToolWindowManagerListener(@NotNull ToolWindowManagerListener listener) {
  }

  /**
   * @return {@code ID} of tool window that was activated last time.
   */
  @Nullable
  public abstract String getLastActiveToolWindowId();

  /**
   * @return {@code ID} of tool window which was last activated among tool windows satisfying the current condition
   */
  @Nullable
  public abstract String getLastActiveToolWindowId(@Nullable Condition<? super JComponent> condition);

  /**
   * @return layout of tool windows.
   */
  @NotNull
  public abstract DesktopLayout getLayout();

  public abstract void setLayoutToRestoreLater(@Nullable DesktopLayout layout);

  @Nullable
  public abstract DesktopLayout getLayoutToRestoreLater();

  /**
   * Copied {@code layout} into internal layout and rearranges tool windows.
   */
  public abstract void setLayout(@NotNull DesktopLayout layout);

  public abstract void clearSideStack();

  public abstract void hideToolWindow(@NotNull String id, boolean hideSide);

  @NotNull
  public abstract List<String> getIdsOn(@NotNull ToolWindowAnchor anchor);

  /*
   * Returns visual representation of tool window location
   * @see AllIcons.Actions#MoveToBottomLeft ... com.intellij.icons.AllIcons.Actions#MoveToWindow icon set
   */
  @NotNull
  public abstract Icon getLocationIcon(@NotNull String id, @NotNull Icon fallbackIcon);
}
