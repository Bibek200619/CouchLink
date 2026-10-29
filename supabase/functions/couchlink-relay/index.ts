import { createClient } from "npm:@supabase/supabase-js@2";

const cors = { "access-control-allow-origin": "*", "access-control-allow-headers": "authorization, apikey, content-type", "access-control-allow-methods": "POST, OPTIONS", "content-type": "application/json" };
const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false } });
const reply = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: cors });
const digest = async (value: string) => Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value))), b => b.toString(16).padStart(2,"0")).join("");
const paths = new Set(["/v1/status","/v1/apps","/v1/frame","/v1/key","/v1/text","/v1/launch","/v1/tap","/v1/unpair","/v1/theme","/v1/studio/state","/v1/studio/scene","/v1/studio/show","/v1/studio/delete","/v1/studio/import/begin","/v1/studio/import/chunk","/v1/studio/import/finish"]);
const allowedPath = (path: string) => paths.has(path) || /^\/v1\/(icon|thumb|media)\?[A-Za-z0-9_=%./-]{1,200}$/.test(path);

Deno.serve(async req => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply({error:"POST required"},405);
  try {
    const input = await req.json();
    if (JSON.stringify(input).length > (input.action === "complete" ? 2_000_000 : 18000)) return reply({error:"Request too large"},413);
    const deviceId = String(input.device_id ?? "");
    if (!/^[A-Za-z0-9-]{1,80}$/.test(deviceId)) return reply({error:"Invalid TV id"},400);
    const action = String(input.action ?? "");
    if (action === "register") {
      const secret = String(input.secret ?? "");
      if (secret.length < 32) return reply({error:"Invalid TV relay credential"},401);
      // PairingGate exposes SHA-256 hashes, not raw phone credentials.
      // Store those hashes as-is; hashing again would make every phone fail auth.
      const tokenHashes = Array.isArray(input.token_hashes) ? input.token_hashes.filter((x: unknown) => typeof x === "string" && /^[a-f0-9]{64}$/.test(x)).slice(0,8) : [];
      const {error} = await admin.from("couchlink_relay_devices").upsert({device_id:deviceId,secret_hash:await digest(secret),token_hashes:tokenHashes,last_seen:new Date().toISOString()});
      if (error) throw error;
      return reply({ok:true});
    }
    if (action === "poll" || action === "complete") {
      const secret = String(input.secret ?? "");
      const {data:device,error} = await admin.from("couchlink_relay_devices").select("secret_hash").eq("device_id",deviceId).maybeSingle();
      if(error)throw error;
      if(!device||device.secret_hash!==await digest(secret))return reply({error:"TV relay credential refused"},401);
      if(action === "poll") {
        await admin.from("couchlink_relay_commands").delete().eq("device_id",deviceId).lt("created_at",new Date(Date.now()-120000).toISOString());
        const {data,error:claimError}=await admin.rpc("couchlink_claim_command",{p_device_id:deviceId});if(claimError)throw claimError;
        return reply({command:data?.[0]??null});
      }
      const id=String(input.command_id??"");if(!/^[0-9a-f-]{36}$/i.test(id))return reply({error:"Invalid command id"},400);
      const {data,error:completeError}=await admin.rpc("couchlink_complete_command",{p_device_id:deviceId,p_command_id:id,p_response:input.response??{error:"Empty response"}});if(completeError)throw completeError;
      return reply({ok:Boolean(data)});
    }
    if(action === "phone" || action === "assistant") {
      const token=String(input.token??""),path=String(input.path??"");
      if(token.length<32)return reply({error:"Invalid remote request"},400);
      const {data:device,error}=await admin.from("couchlink_relay_devices").select("token_hashes").eq("device_id",deviceId).maybeSingle();if(error)throw error;
      if(!device||!device.token_hashes.includes(await digest(token)))return reply({error:"Phone is not paired with this TV"},401);
      if(action === "assistant"){
        const message=String(input.message??"").trim();
        if(!message||message.length>600)return reply({error:"Ask a question up to 600 characters"},400);
        const key=Deno.env.get("OPENROUTER_API_KEY");
        if(!key)return reply({error:"Jarvis AI is not connected yet. Add OPENROUTER_API_KEY to the CouchLink relay secrets."},503);
        const upstream=await fetch("https://openrouter.ai/api/v1/chat/completions",{
          method:"POST",headers:{"authorization":`Bearer ${key}`,"content-type":"application/json","HTTP-Referer":"https://couchlink.app","X-Title":"CouchLink Jarvis"},
          body:JSON.stringify({model:"openrouter/free",messages:[
            {role:"system",content:"You are Jarvis, a concise and friendly assistant for an Android TV remote. Answer general questions accurately and in plain language. Keep voice answers under 90 words. If unsure, say so. Do not claim to see or control the TV. TV commands are handled by CouchLink separately."},
            {role:"user",content:message}
          ],max_tokens:240,temperature:0.4})
        });
        if(!upstream.ok){console.error("OpenRouter request failed",upstream.status);return reply({error:upstream.status===429?"The free model is busy or its daily limit was reached. Try again later.":"The free AI service is temporarily unavailable. Try again in a moment."},502);}
        const result=await upstream.json();
        const answer=String(result?.choices?.[0]?.message?.content??"").trim();
        if(!answer)return reply({error:"The free model returned an empty answer. Try again."},502);
        return reply({answer:answer.slice(0,1200)});
      }
      if(!allowedPath(path))return reply({error:"Invalid remote request"},400);
      const {data:command,error:insertError}=await admin.from("couchlink_relay_commands").insert({device_id:deviceId,request:{path,body:input.body??null},phone_token:token}).select("id").single();if(insertError)throw insertError;
      for(let i=0;i<55;i++){
        const {data,error:readError}=await admin.from("couchlink_relay_commands").select("response").eq("id",command.id).single();if(readError)throw readError;
        if(data?.response){const {error:deleteError}=await admin.from("couchlink_relay_commands").delete().eq("id",command.id);if(deleteError)throw deleteError;return reply(data.response);}
        await new Promise(resolve=>setTimeout(resolve,450));
      }
      return reply({error:"TV is offline or still reconnecting. Try again."},504);
    }
    return reply({error:"Unknown relay action"},400);
  } catch(e) { console.error("relay request failed",String(e).slice(0,300));return reply({error:"Relay request failed"},500); }
});
