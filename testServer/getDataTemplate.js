const axios = require("axios");
const fs = require("fs");
const path = require("path");

// 请求数据
const payload = {
  prompt:
    "1girl, myrtle_\\(arknights\\), pointy_ears, green_eyes, red_hair, ahoge, long_hair, navel, very_long_hair, parted_bangs, <lora:Cute_Anime_Feet_and_Soles_V2.aaaaaa:0.5>, <lora:toes:0.8>, <lora:简彩_V0:0.5>",
  negative_prompt: "",
  styles: ["nai"],
  sampler_name: "Euler",
  scheduler: "ddim",
  steps: 28,
  width: 832,
  height: 1216,
  n_iter: 1,
  cfg_scale: 4,
  seed: -1,
  override_settings: {
    CLIP_stop_at_last_layers: 1,
    sd_model_checkpoint: "noobaiXLNAIXL_vPred10Version",
    sd_vae: "sdxl_vae.safetensors",
  },
};

(async () => {
  try {
    const response = await axios.post(
      "http://192.168.1.114:7860/sdapi/v1/txt2img",
      payload,
      { timeout: 60000 } // 设置超时时间
    );

    // 保存整个响应
    fs.writeFileSync(
      path.join(__dirname, "template.json"),
      JSON.stringify(response.data, null, 2)
    );
    console.log("响应已保存到 template.json");
  } catch (error) {
    console.error("请求失败：", error.message);
  }
})();
