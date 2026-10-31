package com.tcltv.sahne.data

import android.media.tv.TvInputInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputCatalogTest {

    @Test
    fun analogueConnectorsAreListedAsPorts() {
        listOf(
            TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_COMPONENT,
            TvInputInfo.TYPE_SCART,
            TvInputInfo.TYPE_SVIDEO,
            TvInputInfo.TYPE_VGA,
        ).forEach { assertTrue("type $it", InputCatalog.isAnalogPortType(it)) }
    }

    @Test
    fun tunersHdmiAndAppInputsAreNotAnaloguePorts() {
        listOf(TvInputInfo.TYPE_TUNER, TvInputInfo.TYPE_HDMI, TvInputInfo.TYPE_OTHER)
            .forEach { assertFalse("type $it", InputCatalog.isAnalogPortType(it)) }
    }

    @Test
    fun analoguePortsGetTheAvIconAndHdmiPortsTheHdmiOne() {
        val raw = listOf(
            RawHdmiInput("hdmi1", "HDMI 1", true, null, null, true, null),
            RawHdmiInput("av1", "AV 1", false, null, null, false, null),
        )

        val inputs = InputCatalog.resolve(raw, LauncherSettings())

        assertEquals(DeviceLogo.OTHER, inputs.first { it.id == "hdmi1" }.logo)
        assertEquals(DeviceLogo.AV, inputs.first { it.id == "av1" }.logo)
    }
}
