import http from "node:http";
import { URL } from "node:url";

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;
const model = process.env.OPENAI_MODEL || "gpt-6-luna";

const sessions = new Map();
const maxHistory = 8;
const maxBodyBytes = 3_500_000;

if (!apiKey) {
  console.warn("OPENAI_API_KEY is not set. /observe will return 503 until it is configured.");
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(payload),
    "cache-control": "no-store"
  });
  res.end(payload);
}

async function readJson(req) {
  let bytes = 0;
  const chunks = [];
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > maxBodyBytes) throw new Error("body_too_large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function sessionFor(id) {
  if (!sessions.has(id)) {
    sessions.set(id, {
      id,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      observations: []
    });
  }
  return sessions.get(id);
}

function extractOutputText(data) {
  const parts = [];
  for (const item of data?.output || []) {
    for (const content of item?.content || []) {
      if (typeof content?.text === "string") parts.push(content.text);
    }
  }
  return parts.join("\n").trim();
}

function parsePossibleJson(text) {
  try {
    return JSON.parse(text);
  } catch {}

  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start >= 0 && end > start) {
    try {
      return JSON.parse(text.slice(start, end + 1));
    } catch {}
  }

  return {
    app_or_surface: "unknown",
    page_or_context: "unknown",
    likely_user_action: "uncertain",
    likely_task: "uncertain",
    visible_ui: [],
    important_change: "Model returned non-JSON text.",
    sensitive_content_visible: false,
    uncertainty: text || "No model text returned.",
    confidence: 0
  };
}

function compactHistory(session) {
  return session.observations.slice(-6).map((entry, index) => ({
    n: index + 1,
    capturedAt: entry.capturedAt,
    app_or_surface: entry.analysis?.app_or_surface,
    page_or_context: entry.analysis?.page_or_context,
    likely_user_action: entry.analysis?.likely_user_action,
    likely_task: entry.analysis?.likely_task,
    important_change: entry.analysis?.important_change,
    confidence: entry.analysis?.confidence
  }));
}

async function analyzeFrame({ session, imageBase64, capturedAt, width, height }) {
  const prior = compactHistory(session);

  const instructions = `
You are Visual Computer Use Observer, a perception-and-state-tracking system.

You receive the newest literal screenshot from an Android phone plus a compact timeline of prior observations.
Your job is to understand what is actually visible, infer the most likely change/action across time, and maintain a cautious model of the user's current task.

Rules:
- Ground claims in the screenshot and prior timeline.
- Never claim to see taps, text, identities, messages, passwords, account state, or app state that are not actually supported by evidence.
- Separate direct visual evidence from inference.
- If uncertain, say uncertain.
- Do not give instructions to manipulate the device. This observer is read-only.
- Mark sensitive_content_visible=true if the screenshot appears to contain passwords, OTPs, private messages, financial data, authentication screens, personal identifiers, or similarly sensitive content.
- Return JSON only, no markdown.

Return exactly one object with:
{
  "app_or_surface": string,
  "page_or_context": string,
  "likely_user_action": string,
  "likely_task": string,
  "visible_ui": [string],
  "important_change": string,
  "sensitive_content_visible": boolean,
  "uncertainty": string,
  "confidence": number
}

confidence must be between 0 and 1.
`.trim();

  const userText = `
Capture time: ${new Date(capturedAt).toISOString()}
Frame size: ${width}x${height}
Previous timeline:
${JSON.stringify(prior)}

Analyze the newest screenshot as the next state in this timeline.
`.trim();

  const response = await fetch("https://api.openai.com/v1/responses", {
    method: "POST",
    headers: {
      "authorization": `Bearer ${apiKey}`,
      "content-type": "application/json"
    },
    body: JSON.stringify({
      model,
      instructions,
      input: [
        {
          role: "user",
          content: [
            { type: "input_text", text: userText },
            {
              type: "input_image",
              image_url: `data:image/jpeg;base64,${imageBase64}`,
              detail: "low"
            }
          ]
        }
      ],
      max_output_tokens: 650
    })
  });

  const raw = await response.json();

  if (!response.ok) {
    const message = raw?.error?.message || `OpenAI HTTP ${response.status}`;
    throw new Error(message);
  }

  const outputText = extractOutputText(raw);
  return {
    analysis: parsePossibleJson(outputText),
    responseId: raw.id || null
  };
}

const server = http.createServer(async (req, res) => {
  const requestUrl = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

  if (req.method === "GET" && requestUrl.pathname === "/health") {
    return json(res, 200, {
      ok: true,
      service: "visual-computer-use-observer",
      model,
      openaiConfigured: Boolean(apiKey),
      sessions: sessions.size
    });
  }

  if (req.method === "GET" && requestUrl.pathname.startsWith("/state/")) {
    const sessionId = decodeURIComponent(requestUrl.pathname.slice("/state/".length));
    const session = sessions.get(sessionId);

    if (!session) {
      return json(res, 404, { error: "session_not_found" });
    }

    return json(res, 200, {
      id: session.id,
      createdAt: session.createdAt,
      updatedAt: session.updatedAt,
      latest: session.observations.at(-1) || null,
      timeline: session.observations
    });
  }

  if (req.method === "POST" && requestUrl.pathname === "/observe") {
    if (!apiKey) {
      return json(res, 503, { error: "OPENAI_API_KEY_not_configured" });
    }

    try {
      const body = await readJson(req);
      const sessionId = String(body.sessionId || "").trim();
      const imageBase64 = String(body.imageBase64 || "");
      const capturedAt = Number(body.capturedAt || Date.now());
      const width = Number(body.width || 0);
      const height = Number(body.height || 0);

      if (!sessionId || !imageBase64 || !width || !height) {
        return json(res, 400, { error: "invalid_observation_payload" });
      }

      const session = sessionFor(sessionId);
      const result = await analyzeFrame({
        session,
        imageBase64,
        capturedAt,
        width,
        height
      });

      const observation = {
        capturedAt,
        receivedAt: Date.now(),
        width,
        height,
        responseId: result.responseId,
        analysis: result.analysis
      };

      session.observations.push(observation);
      if (session.observations.length > maxHistory) {
        session.observations.splice(0, session.observations.length - maxHistory);
      }
      session.updatedAt = Date.now();

      return json(res, 200, {
        ok: true,
        sessionId,
        observation
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      const status = message === "body_too_large" ? 413 : 500;
      return json(res, status, { error: message });
    }
  }

  return json(res, 404, { error: "not_found" });
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Visual Computer Use observer listening on :${port}`);
});
