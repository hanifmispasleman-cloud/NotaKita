const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
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

test("Unauthenticated user: cannot read store profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("stores").doc("store1").get());
});

test("Authenticated user: cannot read another user's store", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(BOB_UID).collection("stores").doc("store_bob").set({
      userId: BOB_UID,
      storeName: "Warkop Bob",
      managerName: "Bob",
      isOpen: true,
      managerPin: "1234",
      createdAt: new Date(),
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("stores").doc("store_bob").get());
});

test("Authenticated user: can create and read own store profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const storeRef = aliceDb.collection("users").doc(ALICE_UID).collection("stores").doc("store1");

  await assertSucceeds(storeRef.set({
    userId: ALICE_UID,
    storeName: "Warkop Emak",
    managerName: "Emak",
    managerEmail: "emak@warkop.id",
    isOpen: true,
    managerPin: "1234",
    createdAt: new Date(),
  }));

  await assertSucceeds(storeRef.get());
});

test("Authenticated user: can create and read own inventory items", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invRef = aliceDb.collection("users").doc(ALICE_UID).collection("stores").doc("store1").collection("inventory").doc("kopi");

  await assertSucceeds(invRef.set({
    userId: ALICE_UID,
    name: "Kopi Robusta",
    unit: "g",
    stock: 5000,
    costPerUnit: 200,
    minStock: 500,
  }));

  await assertSucceeds(invRef.get());
});

test("Authenticated user: can create cashier sub-account with shift", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const cashierRef = aliceDb.collection("users").doc(ALICE_UID).collection("stores").doc("store1").collection("cashiers").doc("c1");

  await assertSucceeds(cashierRef.set({
    userId: ALICE_UID,
    name: "Kasir Pagi",
    pin: "1111",
    shift: "Pagi (07:00 - 15:00)",
    isActive: true,
  }));

  await assertSucceeds(cashierRef.get());
});

