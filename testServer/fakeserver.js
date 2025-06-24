const express = require("express");
const fs = require("fs");
const path = require("path");
const { createCanvas } = require("canvas");

const app = express();
const port = 7861;

app.use(express.json({ limit: "100mb" }));

let isGenerating = false;
let generationStart = null;
let generationDuration = 0;
let currentProgressImage = null;
let currentProgressCount = null;

function generateRandomFlowerImage(width = 256, height = 256) {
  const canvas = createCanvas(width, height);
  const ctx = canvas.getContext("2d");

  // 随机绘制色块或噪点
  for (let i = 0; i < 1000; i++) {
    ctx.fillStyle = `hsl(${Math.random() * 360}, 100%, 50%)`;
    const x = Math.random() * width;
    const y = Math.random() * height;
    const r = Math.random() * 50;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }

  const buffer = canvas.toBuffer("image/png");
  return buffer.toString("base64");
}

// 加载 fakedata.json
const fakeDataPath = path.join(__dirname, "fakedata.json");
let fakeData = {};
try {
  fakeData = JSON.parse(fs.readFileSync(fakeDataPath, "utf-8"));
} catch (err) {
  console.error("读取 fakedata.json 失败：", err.message);
  process.exit(1);
}

// 注册 GET API 映射
const routes = {
  "/sdapi/v1/sd-models": "sd-models",
  "/sdapi/v1/samplers": "samplers",
  "/sdapi/v1/schedulers": "schedulers",
  "/sdapi/v1/prompt-styles": "prompt-styles",
  "/sdapi/v1/sd-vae": "sd-vae",
  "/sdapi/v1/loras": "loras",
};

for (const [route, key] of Object.entries(routes)) {
  app.get(route, (req, res) => {
    if (key in fakeData) {
      res.json(fakeData[key]);
    } else {
      res.status(404).json({ error: `No fake data for key: ${key}` });
    }
  });
}

// 加载 images 目录下所有图片文件名
const imageDir = path.join(__dirname, "images");
let imageFiles = [];

try {
  imageFiles = fs
    .readdirSync(imageDir)
    .filter(f => /\.(png|jpg|jpeg)$/i.test(f));
  if (imageFiles.length === 0) {
    throw new Error("images 文件夹为空");
  }
} catch (err) {
  console.error("读取 images 文件夹失败：", err.message);
  process.exit(1);
}

// POST /sdapi/v1/txt2img
app.post("/sdapi/v1/txt2img", async (req, res) => {
  if (isGenerating) {
    return res.status(429).json({ error: "Already generating. Please wait." });
  }

  const steps = parseInt(req.body.steps) || 1;
  const n_iter = parseInt(req.body.n_iter) || 1;

  isGenerating = true;
  generationStart = Date.now();
  generationDuration = steps * 1000;
  currentProgressImage = getRandomBase64Image(); // 图像立即准备好

  await new Promise(resolve => setTimeout(resolve, generationDuration));

  const selected = Array.from({ length: n_iter }, () => getRandomBase64Image());

  isGenerating = false;
  generationStart = null;
  generationDuration = 0;
  currentProgressImage = null;
  currentProgressCount = n_iter;
  res.json({ images: selected });
});

function getRandomBase64Image() {
  const file = imageFiles[Math.floor(Math.random() * imageFiles.length)];
  const buffer = fs.readFileSync(path.join(imageDir, file));
  return buffer.toString("base64");
}

function getCurrentImageNumber(progress, jobCount) {
  if (jobCount <= 0) return 0;
  const index = Math.floor(progress * jobCount);
  return Math.min(index + 1, jobCount);
}

app.get("/sdapi/v1/progress", (req, res) => {
  if (isGenerating && generationStart != null) {
    const elapsed = Date.now() - generationStart;
    const progress = Math.min(elapsed / generationDuration, 1);
    const currentIndex = getCurrentImageNumber(progress, currentProgressCount);
    return res.json({
      progress: progress,
      current_image: generateRandomFlowerImage(),
      state: {
        job: `Batch ${currentIndex} out of ${currentProgressCount}`,
        job_count: currentProgressCount,
      },
    });
  } else {
    return res.json({
      progress: 0,
      current_image: generateRandomFlowerImage(),
    });
  }
});

app.listen(port, () => {
  console.log(`Fake server running at http://localhost:${port}`);
});
