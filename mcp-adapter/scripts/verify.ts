/**
 * End-to-end verification against live staging. Exercises the engine directly (not the
 * MCP transport) so each domain slice can be proven in one run. This is the harness the
 * overnight loop uses per domain — extend it as tools are added.
 *
 * Proves, for the leave slice:
 *   1. HR sees a real pending queue (read: id + summary both populated).
 *   2. approve_leave takes effect (write: queue drops by exactly 1, that id gone).
 *   3. Employee sees an EMPTY queue — RBAC enforced by the app's own front door.
 */
import { FrontDoor } from "../src/engine.js";
import { config } from "../src/config.js";

const list = config.readTools.find((t) => t.name === "list_pending_approvals")!;
const approve = config.writeTools.find((t) => t.name === "approve_leave")!;
const timeList = config.readTools.find((t) => t.name === "list_time_approvals")!;
const timeApprove = config.writeTools.find((t) => t.name === "approve_timesheet")!;

function assert(cond: boolean, msg: string): void {
  if (!cond) throw new Error(msg);
}

async function main(): Promise<void> {
  console.log(`Target: ${config.baseUrl}\n`);

  // --- HR read + write ---
  const hr = new FrontDoor(config);
  await hr.bootstrap("priya.nair");
  const before = await hr.read(list);
  console.log(`[HR priya.nair] pending queue: ${before.length}`);
  for (const r of before) console.log(`   ${r.id ?? "(no-id)"}  ·  ${r.summary}`);
  assert(before.length > 0, "expected HR to have a pending queue on a fresh workspace");
  assert(before.every((r) => r.id && r.summary), "every row must have both an id and a summary");

  const target = before.find((r) => r.id)!;
  console.log(`\n[write] approve_leave(id=${target.id})`);
  const res = await hr.write(approve, { id: target.id });
  console.log(`   → ${JSON.stringify(res)}`);

  const after = await hr.read(list);
  console.log(`[HR] queue after approve: ${after.length} (was ${before.length})`);
  assert(!after.some((r) => r.id === target.id), "approved id should be gone from the queue");
  assert(after.length === before.length - 1, "queue should drop by exactly 1");

  // --- Employee: RBAC-for-free (own isolated workspace via separate cookie jar) ---
  const emp = new FrontDoor(config);
  await emp.bootstrap("sarah.chen");
  const empQueue = await emp.read(list);
  console.log(`\n[EMPLOYEE sarah.chen] pending queue: ${empQueue.length} (expect 0)`);
  assert(empQueue.length === 0, "employee is not an approver — queue must be empty");

  console.log("\n✅ leave slice verified end-to-end against staging");

  // --- Time: read pending timesheets + approve one (composite empId+week handle) ---
  const tm = new FrontDoor(config);
  await tm.bootstrap("priya.nair");
  const tBefore = await tm.read(timeList);
  console.log(`\n[HR] pending timesheets: ${tBefore.length}`);
  for (const r of tBefore) console.log(`   ${r.empId} / ${r.week}  ·  ${r.summary}`);
  assert(tBefore.length > 0, "expected pending timesheets on a fresh workspace");
  assert(tBefore.every((r) => r.empId && r.week && r.summary), "each timesheet row needs empId + week + summary");

  const tTarget = tBefore[0];
  console.log(`\n[write] approve_timesheet(empId=${tTarget.empId}, week=${tTarget.week})`);
  const tRes = await tm.write(timeApprove, { empId: tTarget.empId, week: tTarget.week });
  console.log(`   → ${JSON.stringify(tRes)}`);

  const tAfter = await tm.read(timeList);
  console.log(`[HR] pending timesheets after: ${tAfter.length} (was ${tBefore.length})`);
  assert(
    !tAfter.some((r) => r.empId === tTarget.empId && r.week === tTarget.week),
    "approved timesheet should be gone",
  );
  assert(tAfter.length === tBefore.length - 1, "pending timesheets should drop by exactly 1");

  // --- Employee: no timesheet approvals (RBAC-for-free) ---
  const empT = new FrontDoor(config);
  await empT.bootstrap("sarah.chen");
  const empTimeQueue = await empT.read(timeList);
  console.log(`\n[EMPLOYEE sarah.chen] pending timesheets: ${empTimeQueue.length} (expect 0)`);
  assert(empTimeQueue.length === 0, "employee is not an approver — timesheet queue must be empty");

  console.log("\n✅ time slice verified end-to-end against staging");
}

main().catch((e) => {
  console.error(`\n❌ ${(e as Error).message}`);
  process.exit(1);
});
