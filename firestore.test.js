const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const TEST_TS = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write user conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("conversations").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("User cannot access another user's conversations or projects", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("conversations").doc("conv_1").set({
      userId: BOB_UID,
      title: "Bob Secret",
      mode: "chat",
      language: "Indonesia",
      previewText: "Halo",
      messageCount: 1,
      createdAt: TEST_TS(),
      updatedAt: TEST_TS(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("conversations").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("conversations").doc("conv_1").get());
});

test("Owner can create, update, and list their own conversation and messages", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const convRef = aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("conv_1");

  await assertSucceeds(convRef.set({
    userId: ALICE_UID,
    title: "Analisis Data Penjualan",
    mode: "analysis",
    language: "Indonesia",
    previewText: "Mulai analisis",
    messageCount: 0,
    createdAt: TEST_TS(),
    updatedAt: TEST_TS(),
  }));

  await assertSucceeds(convRef.collection("messages").doc("msg_1").set({
    userId: ALICE_UID,
    conversationId: "conv_1",
    role: "user",
    text: "Tolong analisis CSV ini",
    attachmentName: "sales.csv",
    attachmentType: "file",
    createdAt: TEST_TS(),
  }));

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("conversations").get());
});

test("Shadow update with ghost field is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const projectRef = aliceDb.collection("users").doc(ALICE_UID).collection("projects").doc("proj_1");

  await assertFails(projectRef.set({
    userId: ALICE_UID,
    title: "Landing Page",
    language: "HTML/Web",
    prompt: "Buat landing page",
    code: "<h1>Hi</h1>",
    isWebPreviewable: true,
    isAdmin: true,
    createdAt: TEST_TS(),
    updatedAt: TEST_TS(),
  }));
});

test("Invalid document ID or missing parent rejects message create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("conversations").doc("nonexistent").collection("messages").doc("msg_1").set({
      userId: ALICE_UID,
      conversationId: "nonexistent",
      role: "user",
      text: "Orphan",
      createdAt: TEST_TS(),
    })
  );
});

test("Owner can create coding lesson and update step completion", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const lessonRef = aliceDb.collection("users").doc(ALICE_UID).collection("lessons").doc("lesson_1");

  await assertSucceeds(lessonRef.set({
    userId: ALICE_UID,
    topic: "Dasar Kotlin Coroutines",
    programmingLanguage: "Kotlin",
    difficulty: "Pemula",
    currentStepIndex: 0,
    totalSteps: 3,
    completedSteps: 0,
    createdAt: TEST_TS(),
    updatedAt: TEST_TS(),
  }));

  const stepRef = lessonRef.collection("steps").doc("step_0");
  await assertSucceeds(stepRef.set({
    userId: ALICE_UID,
    lessonId: "lesson_1",
    stepIndex: 0,
    title: "Pengenalan suspend",
    explanation: "Fungsi suspend dapat dijeda tanpa memblokir thread.",
    codeExample: "suspend fun fetch() {}",
    challengePrompt: "Buat fungsi suspend bernama loadData",
    hint: "Gunakan keyword suspend",
    userAnswer: "",
    aiFeedback: "",
    isCompleted: false,
    createdAt: TEST_TS(),
    updatedAt: TEST_TS(),
  }));

  await assertSucceeds(stepRef.update({
    userAnswer: "suspend fun loadData() {}",
    aiFeedback: "Bagus sekali!",
    isCompleted: true,
    updatedAt: TEST_TS(),
  }));
});
