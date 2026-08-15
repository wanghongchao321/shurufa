import "dotenv/config";
import { createApp } from "./app.js";

const port = Number(process.env.PORT || 8787);
const app = await createApp();

app.listen(port, "0.0.0.0", () => {
  console.log(`Voice IME backend listening on port ${port}`);
});
