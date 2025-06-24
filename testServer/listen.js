const express = require("express");
const fs = require("fs");
const app = express();
const port = 7861;

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

const logFile = "request_log.txt";

app.all("*", (req, res) => {
  const logEntry = {
    time: new Date().toISOString(),
    method: req.method,
    path: req.originalUrl,
    headers: req.headers,
    body: req.body,
  };

  fs.appendFileSync(logFile, JSON.stringify(logEntry, null, 2) + "\n\n");

  res.json({
    status: "ok",
    received: logEntry,
  });
});

app.listen(port, () => {
  console.log(`Fake server running on http://localhost:${port}`);
});
