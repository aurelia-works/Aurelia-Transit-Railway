<template>
  <div>
    <Group v-if="showViewControls" :title="$t('controls.title')" >
      <SimpleButton v-if="mapViewer.map.perspectiveView" :active="appState.controls.state === 'perspective'" @action="$bluemap.setPerspectiveView(500, appState.controls.state === 'free' ? 100 : 0)">{{$t('controls.perspective.button')}}</SimpleButton>
      <SimpleButton v-if="mapViewer.map.flatView" :active="appState.controls.state === 'flat'" @action="$bluemap.setFlatView(500, appState.controls.state === 'free' ? 100 : 0)">{{$t('controls.flatView.button')}}</SimpleButton>
      <SimpleButton v-if="mapViewer.map.freeFlightView" :active="appState.controls.state === 'free'" @action="$bluemap.setFreeFlight(500)">{{$t('controls.freeFlight.button')}}</SimpleButton>
    </Group>

    <Group class="atr-group" title="ATR layer">
      <SwitchButton :on="atr.enabled" @action="atr.enabled = !atr.enabled; applyAtr()">Show the ATR layer</SwitchButton>
      <template v-if="atr.enabled">
        <SwitchButton :on="atr.lines" @action="atr.lines = !atr.lines; applyAtr()">Lines</SwitchButton>
        <Slider v-if="atr.lines" :value="atr.lineWidth" :min="1" :max="12" :step="1" @update="atr.lineWidth = $event; applyAtr()">Line width</Slider>
        <Slider v-if="atr.lines" :value="atr.lineOpacity" :min="0.1" :max="1" :step="0.05" @update="atr.lineOpacity = $event; applyAtr()">Line opacity</Slider>
        <SwitchButton v-if="atr.lines" :on="atr.straightLines" @action="atr.straightLines = !atr.straightLines; applyAtr()">Straight lines where no track is found</SwitchButton>
        <SwitchButton :on="atr.stations" @action="atr.stations = !atr.stations; applyAtr()">Stations</SwitchButton>
        <Slider v-if="atr.stations" :value="atr.labelDistance" :min="0.2" :max="4" :step="0.1" @update="atr.labelDistance = $event; applyAtr()">Label distance</Slider>
        <label v-if="atr.stations" class="atr-color">Label colour <input type="color" :value="atr.labelColor" @input="atr.labelColor = $event.target.value; applyAtr()"></label>
        <SwitchButton :on="atr.trains" @action="atr.trains = !atr.trains; applyAtr()">Trains</SwitchButton>
        <Slider v-if="atr.trains" :value="atr.trainSize" :min="0.5" :max="3" :step="0.1" @update="atr.trainSize = $event; applyAtr()">Train icon size</Slider>
        <label v-if="atr.trains" class="atr-color">Train colour <input type="color" :value="atr.trainColor || '#e53935'" @input="atr.trainColor = $event.target.value; applyAtr()">
          <button v-if="atr.trainColor" @click="atr.trainColor = ''; applyAtr()">use route colours</button><span v-else>(route colours)</span></label>
        <SimpleButton v-for="mode of atrNameModes" :key="mode.value" :active="atr.nameMode === mode.value" @action="atr.nameMode = mode.value; applyAtr()">{{ mode.name }}</SimpleButton>
        <div class="atr-api">ATR server<TextInput :value="atr.apiUrl" @input="atr.apiUrl = $event.target.value" @change="applyAtrServer()" placeholder="same host, port 8888" /></div>
      </template>
    </Group>

    <Group :title="$t('lighting.title')">
      <Slider :value="mapViewer.uniforms.sunlightStrength.value" :min="0" :max="1" :step="0.01"
              @update="mapViewer.uniforms.sunlightStrength.value = $event; $bluemap.mapViewer.redraw()">{{$t('lighting.sunlight')}}</Slider>
      <Slider :value="mapViewer.uniforms.ambientLight.value" :min="0" :max="1" :step="0.01"
              @update="mapViewer.uniforms.ambientLight.value = $event; $bluemap.mapViewer.redraw()">{{$t('lighting.ambientLight')}}</Slider>
    </Group>

    <Group :title="$t('resolution.title')">
      <SimpleButton v-for="stage of qualityStages" :key="stage.name"
                    :active="mapViewer.superSampling === stage.value"
                    @action="$bluemap.mapViewer.superSampling = stage.value; $bluemap.saveUserSettings(); $bluemap.mapViewer.redraw()"
      >{{stage.name}}</SimpleButton>
    </Group>

    <Group :title="$t('renderDistance.title')">
      <Slider :value="mapViewer.loadedHiresViewDistance" :min="settings.hiresSliderMin" :max="settings.hiresSliderMax" :step="10" :formatter="renderDistanceFormatter"
              @update="mapViewer.loadedHiresViewDistance = $event; $bluemap.mapViewer.updateLoadedMapArea()" @lazy="$bluemap.saveUserSettings()">{{ $t("renderDistance.hiresLayer") }}</Slider>
      <Slider :value="mapViewer.loadedLowresViewDistance" :min="settings.lowresSliderMin" :max="settings.lowresSliderMax" :step="100"
              @update="mapViewer.loadedLowresViewDistance = $event; $bluemap.mapViewer.updateLoadedMapArea()" @lazy="$bluemap.saveUserSettings()">{{ $t("renderDistance.lowersLayer") }}</Slider>
      <SwitchButton :on="!appState.controls.pauseTileLoading" @action="appState.controls.pauseTileLoading = !appState.controls.pauseTileLoading; $bluemap.saveUserSettings()">{{ $t("renderDistance.loadHiresWhileMoving") }}</SwitchButton>
    </Group>

    <Group :title="$t('mapControls.title')">
      <SwitchButton :on="appState.controls.showZoomButtons" @action="appState.controls.showZoomButtons = !appState.controls.showZoomButtons; $bluemap.saveUserSettings()">{{ $t("mapControls.showZoomButtons") }}</SwitchButton>
    </Group>

    <Group :title="$t('freeFlightControls.title')">
      <Slider :value="appState.controls.mouseSensitivity" :min="0.1" :max="5" :step="0.05"
              @update="appState.controls.mouseSensitivity = $event; $bluemap.updateControlsSettings();" @lazy="$bluemap.saveUserSettings()">{{ $t("freeFlightControls.mouseSensitivity") }}</Slider>
      <SwitchButton :on="appState.controls.invertMouse" @action="appState.controls.invertMouse = !appState.controls.invertMouse; $bluemap.updateControlsSettings(); $bluemap.saveUserSettings()">{{ $t("freeFlightControls.invertMouseY") }}</SwitchButton>
    </Group>

    <Group :title="$t('theme.title')">
      <SimpleButton v-for="theme of themes" :key="theme.name"
                    :active="appState.theme === theme.value"
                    @action="$bluemap.setTheme(theme.value); $bluemap.saveUserSettings();"
      >{{theme.name}}</SimpleButton>
    </Group>

    <Group :title="$t('screenshot.title')">
      <SwitchButton :on="appState.screenshot.clipboard" @action="appState.screenshot.clipboard = !appState.screenshot.clipboard; $bluemap.saveUserSettings()">{{ $t("screenshot.clipboard") }}</SwitchButton>
    </Group>

    <Group v-if="languages.length > 1" :title="$t('language.title')">
      <SimpleButton v-for="lang of languages" :key="lang.locale"
                    :active="lang.locale === $i18n.locale"
                    @action="changeLanguage(lang.locale);"
      >{{lang.name}}</SimpleButton>
    </Group>

    <SwitchButton :on="mapViewer.uniforms.chunkBorders.value" @action="switchChunkBorders(); $bluemap.saveUserSettings();">{{ $t("chunkBorders.button") }}</SwitchButton>

    <SwitchButton :on="appState.debug" @action="switchDebug(); $bluemap.saveUserSettings();">{{ $t("debug.button") }}</SwitchButton>

    <SimpleButton @action="$bluemap.resetSettings()">{{ $t("resetAllSettings.button") }}</SimpleButton>
  </div>
</template>

<script>
import Group from "./Group.vue";
import SimpleButton from "./SimpleButton.vue";
import Slider from "./Slider.vue";
import SwitchButton from "./SwitchButton.vue";
import TextInput from "./TextInput.vue";
import {i18n, setLanguage} from "../../i18n";

const themes = [
  {get name(){ return i18n.t("theme.default")}, value: null},
  {get name(){ return i18n.t("theme.dark")}, value: 'dark'},
  {get name(){ return i18n.t("theme.light")}, value: 'light'},
  {get name(){ return i18n.t("theme.contrast")}, value: 'contrast'},
  {get name(){ return i18n.t("theme.black")}, value: 'black'},
];

const atrNameModes = [
  {name: "Names: English", value: "latin"},
  {name: "Names: first line", value: "first"},
  {name: "Names: all lines", value: "all"},
];

const qualityStages = [
  {get name(){ return i18n.t("resolution.high")}, value: 2},
  {get name(){ return i18n.t("resolution.normal")}, value: 1},
  {get name(){ return i18n.t("resolution.low")}, value: 0.5},
];

export default {
name: "SettingsMenu",
  components: {SwitchButton, Slider, SimpleButton, Group, TextInput},
  data() {
    return {
      appState: this.$bluemap.appState,
      atr: this.$bluemap.appState.atr,
      atrNameModes: atrNameModes,
      mapViewer: this.$bluemap.mapViewer.data,
      settings: {
        ...{
          hiresSliderMax: 500,
          hiresSliderMin: 50,
          lowresSliderMax: 10000,
          lowresSliderMin: 500
        },
        ...this.$bluemap.settings
      },
      languages: i18n.languages,

      qualityStages: qualityStages,
      themes: themes,
    }
  },
  computed: {
    showViewControls() {
      if (!this.mapViewer.map) return false;
      return this.mapViewer.map.views.length > 1;
    }
  },
  methods: {
    applyAtr() {
      this.$bluemap.atrLayer.applySettings();
    },
    applyAtrServer() {
      this.$bluemap.atrLayer.dimensions = null;
      this.$bluemap.atrLayer.stop();
      this.$bluemap.atrLayer.applySettings();
    },
    switchChunkBorders() {
      this.$bluemap.setChunkBorders(!this.mapViewer.uniforms.chunkBorders.value);
    },
    switchDebug() {
      this.$bluemap.setDebug(!this.appState.debug);
    },
    renderDistanceFormatter(value) {
      let f = parseFloat(value);
      return f === 0 ? this.$t("renderDistance.off") : f.toFixed(0);
    },
    async changeLanguage(lang) {
        await setLanguage(lang);
        this.$bluemap.updatePageAddress()
        this.$bluemap.saveUserSettings();
    }
  }
}
</script>

<style>

</style>
