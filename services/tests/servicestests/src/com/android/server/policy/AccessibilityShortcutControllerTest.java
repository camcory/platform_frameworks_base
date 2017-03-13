/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.policy;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Vibrator;
import android.provider.Settings;
import android.support.test.runner.AndroidJUnit4;

import android.test.mock.MockContentResolver;
import android.text.TextUtils;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.IAccessibilityManager;
import android.widget.Toast;
import com.android.internal.R;
import com.android.internal.util.test.FakeSettingsProvider;
import com.android.server.policy.AccessibilityShortcutController.FrameworkObjectProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.internal.util.reflection.Whitebox;

import java.util.Collections;

import static android.provider.Settings.Secure.ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN;
import static android.provider.Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class AccessibilityShortcutControllerTest {
    private static final String SERVICE_NAME_STRING = "fake.package/fake.service.name";
    private static final long VIBRATOR_PATTERN_1 = 100L;
    private static final long VIBRATOR_PATTERN_2 = 150L;
    private static final int[] VIBRATOR_PATTERN_INT = {(int) VIBRATOR_PATTERN_1,
            (int) VIBRATOR_PATTERN_2};
    private static final long[] VIBRATOR_PATTERN_LONG = {VIBRATOR_PATTERN_1, VIBRATOR_PATTERN_2};

    private @Mock Context mContext;
    private @Mock FrameworkObjectProvider mFrameworkObjectProvider;
    private @Mock IAccessibilityManager mAccessibilityManagerService;
    private @Mock Handler mHandler;
    private @Mock AlertDialog.Builder mAlertDialogBuilder;
    private @Mock AlertDialog mAlertDialog;
    private @Mock AccessibilityServiceInfo mServiceInfo;
    private @Mock Resources mResources;
    private @Mock Toast mToast;
    private @Mock Vibrator mVibrator;
    private @Mock ApplicationInfo mApplicationInfo;

    private MockContentResolver mContentResolver;
    private WindowManager.LayoutParams mLayoutParams = new WindowManager.LayoutParams();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mVibrator.hasVibrator()).thenReturn(true);

        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getApplicationInfo()).thenReturn(mApplicationInfo);
        when(mContext.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(mVibrator);

        mContentResolver = new MockContentResolver(mContext);
        mContentResolver.addProvider(Settings.AUTHORITY, new FakeSettingsProvider());
        when(mContext.getContentResolver()).thenReturn(mContentResolver);

        when(mAccessibilityManagerService.getInstalledAccessibilityServiceList(anyInt()))
                .thenReturn(Collections.singletonList(mServiceInfo));

        // Use the extra level of indirection in the object to mock framework objects
        AccessibilityManager accessibilityManager =
                new AccessibilityManager(mHandler, mAccessibilityManagerService, 0);
        when(mFrameworkObjectProvider.getAccessibilityManagerInstance(mContext))
                .thenReturn(accessibilityManager);
        when(mFrameworkObjectProvider.getAlertDialogBuilder(mContext))
                .thenReturn(mAlertDialogBuilder);
        when(mFrameworkObjectProvider.makeToastFromText(eq(mContext), anyObject(), anyInt()))
                .thenReturn(mToast);

        when(mResources.getString(anyInt())).thenReturn("Howdy %s");
        when(mResources.getIntArray(anyInt())).thenReturn(VIBRATOR_PATTERN_INT);

        ResolveInfo resolveInfo = mock(ResolveInfo.class);
        when(resolveInfo.loadLabel(anyObject())).thenReturn("Service name");
        when(mServiceInfo.getResolveInfo()).thenReturn(resolveInfo);
        when(mServiceInfo.getComponentName())
                .thenReturn(ComponentName.unflattenFromString(SERVICE_NAME_STRING));

        when(mAlertDialogBuilder.setTitle(anyInt())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setCancelable(anyBoolean())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setMessage(anyObject())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setPositiveButton(anyInt(), anyObject()))
                .thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setNegativeButton(anyInt(), anyObject()))
                .thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.setOnCancelListener(anyObject())).thenReturn(mAlertDialogBuilder);
        when(mAlertDialogBuilder.create()).thenReturn(mAlertDialog);

        mLayoutParams.privateFlags = 0;
        when(mToast.getWindowParams()).thenReturn(mLayoutParams);

        Window window = mock(Window.class);
        Whitebox.setInternalState(window, "mWindowAttributes", new WindowManager.LayoutParams());
        when(mAlertDialog.getWindow()).thenReturn(window);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testShortcutAvailable_withNullServiceIdWhenCreated_shouldReturnFalse() {
        configureShortcutDisabled();
        assertFalse(getController().isAccessibilityShortcutAvailable());
    }

    @Test
    public void testShortcutAvailable_withNonNullServiceIdWhenCreated_shouldReturnTrue() {
        configureShortcutEnabled();
        assertTrue(getController().isAccessibilityShortcutAvailable());
    }

    @Test
    public void testShortcutAvailable_whenServiceIdBecomesNull_shouldReturnFalse() {
        configureShortcutEnabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        Settings.Secure.putString(mContentResolver, ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, "");
        accessibilityShortcutController.onSettingsChanged();
        assertFalse(accessibilityShortcutController.isAccessibilityShortcutAvailable());
    }

    @Test
    public void testShortcutAvailable_whenServiceIdBecomesNonNull_shouldReturnTrue() {
        configureShortcutDisabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        configureShortcutEnabled();
        accessibilityShortcutController.onSettingsChanged();
        assertTrue(accessibilityShortcutController.isAccessibilityShortcutAvailable());
    }

    @Test
    public void testOnAccessibilityShortcut_vibrates() {
        configureShortcutEnabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        accessibilityShortcutController.performAccessibilityShortcut();
        verify(mVibrator).vibrate(aryEq(VIBRATOR_PATTERN_LONG), eq(-1), anyObject());
    }

    @Test
    public void testOnAccessibilityShortcut_firstTime_showsWarningDialog()
            throws Exception {
        configureShortcutEnabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0);
        accessibilityShortcutController.performAccessibilityShortcut();

        assertEquals(1, Settings.Secure.getInt(
                mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0));
        verify(mResources).getString(R.string.accessibility_shortcut_toogle_warning);
        verify(mAlertDialog).show();
        verify(mAccessibilityManagerService).getInstalledAccessibilityServiceList(anyInt());
        verify(mAccessibilityManagerService, times(0)).performAccessibilityShortcut();
    }

    @Test
    public void testOnAccessibilityShortcut_withDialogShowing_callsServer()
        throws Exception {
        configureShortcutEnabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0);
        accessibilityShortcutController.performAccessibilityShortcut();
        accessibilityShortcutController.performAccessibilityShortcut();
        verify(mToast).show();
        assertEquals(WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS,
                mLayoutParams.privateFlags
                        & WindowManager.LayoutParams.PRIVATE_FLAG_SHOW_FOR_ALL_USERS);
        verify(mAccessibilityManagerService, times(1)).performAccessibilityShortcut();
    }

    @Test
    public void testOnAccessibilityShortcut_ifCanceledFirstTime_showsWarningDialog()
        throws Exception {
        configureShortcutEnabled();
        AccessibilityShortcutController accessibilityShortcutController = getController();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0);
        accessibilityShortcutController.performAccessibilityShortcut();
        ArgumentCaptor<AlertDialog.OnCancelListener> cancelListenerCaptor =
                ArgumentCaptor.forClass(AlertDialog.OnCancelListener.class);
        verify(mAlertDialogBuilder).setOnCancelListener(cancelListenerCaptor.capture());
        // Call the cancel callback
        cancelListenerCaptor.getValue().onCancel(null);

        accessibilityShortcutController.performAccessibilityShortcut();
        verify(mAlertDialog, times(2)).show();
    }

    @Test
    public void testClickingDisableButtonInDialog_shouldClearShortcutId() {
        configureShortcutEnabled();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0);
        getController().performAccessibilityShortcut();

        ArgumentCaptor<DialogInterface.OnClickListener> captor =
                ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mAlertDialogBuilder).setNegativeButton(eq(R.string.disable_accessibility_shortcut),
                captor.capture());
        // Call the button callback
        captor.getValue().onClick(null, 0);
        assertTrue(TextUtils.isEmpty(
                Settings.Secure.getString(mContentResolver, ACCESSIBILITY_SHORTCUT_TARGET_SERVICE)));
    }

    @Test
    public void testClickingLeaveOnButtonInDialog_shouldLeaveShortcutReady() throws Exception {
        configureShortcutEnabled();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 0);
        getController().performAccessibilityShortcut();

        ArgumentCaptor<DialogInterface.OnClickListener> captor =
            ArgumentCaptor.forClass(DialogInterface.OnClickListener.class);
        verify(mAlertDialogBuilder).setPositiveButton(eq(R.string.leave_accessibility_shortcut_on),
            captor.capture());
        // Call the button callback, if one exists
        if (captor.getValue() != null) {
            captor.getValue().onClick(null, 0);
        }
        assertEquals(SERVICE_NAME_STRING,
                Settings.Secure.getString(mContentResolver, ACCESSIBILITY_SHORTCUT_TARGET_SERVICE));
        assertEquals(1, Settings.Secure.getInt(
            mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN));
    }

    @Test
    public void testOnAccessibilityShortcut_afterDialogShown_shouldCallServer() throws Exception {
        configureShortcutEnabled();
        Settings.Secure.putInt(mContentResolver, ACCESSIBILITY_SHORTCUT_DIALOG_SHOWN, 1);
        getController().performAccessibilityShortcut();

        verifyZeroInteractions(mAlertDialogBuilder, mAlertDialog);
        verify(mToast).show();
        verify(mAccessibilityManagerService).performAccessibilityShortcut();
    }

    private void configureShortcutDisabled() {
        Settings.Secure.putString(mContentResolver, ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, "");
    }

    private void configureShortcutEnabled() {
        Settings.Secure.putString(
                mContentResolver, ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, SERVICE_NAME_STRING);
    }

    private AccessibilityShortcutController getController() {
        AccessibilityShortcutController accessibilityShortcutController =
                new AccessibilityShortcutController(mContext, mHandler);
        accessibilityShortcutController.mFrameworkObjectProvider = mFrameworkObjectProvider;
        return accessibilityShortcutController;
    }
}
