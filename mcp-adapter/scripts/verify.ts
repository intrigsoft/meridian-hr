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
import type { Row } from "../src/engine.js";
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

  // --- Job changes: approve + reject (path-id, HR-only) ---
  const jcList = config.readTools.find((t) => t.name === "list_job_changes")!;
  const jcApprove = config.writeTools.find((t) => t.name === "approve_job_change")!;
  const jcReject = config.writeTools.find((t) => t.name === "reject_job_change")!;

  const jc = new FrontDoor(config);
  await jc.bootstrap("priya.nair");
  const jcBefore = await jc.read(jcList);
  console.log(`\n[HR] pending job changes: ${jcBefore.length}`);
  for (const r of jcBefore) console.log(`   ${r.id}  ·  ${r.summary}`);
  assert(jcBefore.length > 0, "expected a pending job change on a fresh workspace");
  assert(jcBefore.every((r) => r.id && r.summary), "each job-change row needs id + summary");

  const jcTarget = jcBefore[0];
  console.log(`\n[write] approve_job_change(id=${jcTarget.id})`);
  console.log(`   → ${JSON.stringify(await jc.write(jcApprove, { id: jcTarget.id }))}`);
  const jcAfter = await jc.read(jcList);
  console.log(`[HR] pending job changes after approve: ${jcAfter.length} (was ${jcBefore.length})`);
  assert(!jcAfter.some((r) => r.id === jcTarget.id), "approved job change should leave the pending list");

  // reject in a fresh workspace (proves the second write route)
  const jc2 = new FrontDoor(config);
  await jc2.bootstrap("priya.nair");
  const jc2Before = await jc2.read(jcList);
  const rTarget = jc2Before[0];
  console.log(`\n[write] reject_job_change(id=${rTarget.id})`);
  console.log(`   → ${JSON.stringify(await jc2.write(jcReject, { id: rTarget.id }))}`);
  const jc2After = await jc2.read(jcList);
  assert(!jc2After.some((r) => r.id === rTarget.id), "rejected job change should leave the pending list");

  // employee: restricted panel → empty
  const jcEmp = new FrontDoor(config);
  await jcEmp.bootstrap("sarah.chen");
  const jcEmpQueue = await jcEmp.read(jcList);
  console.log(`\n[EMPLOYEE sarah.chen] pending job changes: ${jcEmpQueue.length} (expect 0)`);
  assert(jcEmpQueue.length === 0, "employee may not view/approve job changes");

  console.log("\n✅ jobchange slice verified end-to-end against staging");

  // --- Recruitment: approve a requisition pending approval (HR-only, path-id) ---
  const reqList = config.readTools.find((t) => t.name === "list_requisition_approvals")!;
  const reqApprove = config.writeTools.find((t) => t.name === "approve_requisition")!;

  const rc = new FrontDoor(config);
  await rc.bootstrap("priya.nair");
  const rcBefore = await rc.read(reqList);
  console.log(`\n[HR] requisitions awaiting approval: ${rcBefore.length}`);
  for (const r of rcBefore) console.log(`   ${r.id}  ·  ${r.summary}`);
  assert(rcBefore.length > 0, "expected a requisition pending approval on a fresh workspace");
  assert(rcBefore.every((r) => r.id && r.summary), "each requisition row needs id + summary");

  const rcTarget = rcBefore[0];
  console.log(`\n[write] approve_requisition(id=${rcTarget.id})`);
  console.log(`   → ${JSON.stringify(await rc.write(reqApprove, { id: rcTarget.id }))}`);
  const rcAfter = await rc.read(reqList);
  console.log(`[HR] awaiting approval after: ${rcAfter.length} (was ${rcBefore.length})`);
  assert(!rcAfter.some((r) => r.id === rcTarget.id), "approved requisition should leave the pending list");

  const rcEmp = new FrontDoor(config);
  await rcEmp.bootstrap("sarah.chen");
  const rcEmpQueue = await rcEmp.read(reqList);
  console.log(`\n[EMPLOYEE sarah.chen] requisition approvals: ${rcEmpQueue.length} (expect 0)`);
  assert(rcEmpQueue.length === 0, "employee may not view/approve requisitions");

  console.log("\n✅ recruitment (requisition approval) slice verified end-to-end against staging");

  // --- Directory: search + get_employee (read-args; base-role, available to everyone) ---
  const search = config.readTools.find((t) => t.name === "search_directory")!;
  const getEmp = config.readTools.find((t) => t.name === "get_employee")!;

  const dir = new FrontDoor(config);
  await dir.bootstrap("sarah.chen"); // employee — proves the directory is base-role, not HR-gated
  const hits = await dir.read(search, { q: "chen" });
  console.log(`\n[EMPLOYEE] search_directory("chen"): ${hits.length} hit(s)`);
  for (const r of hits) console.log(`   ${r.id}  ·  ${r.summary}`);
  assert(hits.length > 0, "search should find at least one person for 'chen'");
  assert(hits.some((r) => r.id === "sarah.chen"), "search 'chen' should include sarah.chen");
  assert(hits.every((r) => r.id && r.summary), "each directory hit needs id + summary");

  const empty = await dir.read(search, { q: "zzz-no-such-person" });
  console.log(`[EMPLOYEE] search_directory("zzz…"): ${empty.length} (expect 0 via empty marker)`);
  assert(empty.length === 0, "a no-match search must return an empty list, not fail loud");

  const prof = await dir.read(getEmp, { id: "sarah.chen" });
  console.log(`\n[EMPLOYEE] get_employee("sarah.chen"): ${prof.length} profile`);
  assert(prof.length === 1, "get_employee should return exactly one profile region");
  assert(prof[0].summary.includes("Sarah Chen"), "profile summary should contain the employee's name");
  console.log(`   summary(head): ${prof[0].summary.slice(0, 140)}…`);

  console.log("\n✅ directory slice verified end-to-end against staging");

  // --- Onboarding: cases → steps → complete a step (chain; verify-by-absence) ---
  const obCases = config.readTools.find((t) => t.name === "list_onboarding_cases")!;
  const obSteps = config.readTools.find((t) => t.name === "list_onboarding_steps")!;
  const obComplete = config.writeTools.find((t) => t.name === "complete_onboarding_step")!;

  const ob = new FrontDoor(config);
  await ob.bootstrap("priya.nair");
  const cases = await ob.read(obCases);
  console.log(`\n[HR] onboarding cases: ${cases.length}`);
  for (const c of cases) console.log(`   ${c.id}  ·  ${c.summary}`);
  assert(cases.length > 0, "expected seeded onboarding cases");
  assert(cases.every((c) => c.id && c.summary), "each case needs id + summary");

  let chosen: Row | null = null;
  let steps: Row[] = [];
  for (const c of cases) {
    const s = await ob.read(obSteps, { caseId: c.id });
    if (s.length > 0) {
      chosen = c;
      steps = s;
      break;
    }
  }
  assert(chosen !== null, "expected at least one case with an in-progress step");
  console.log(`\n[HR] actionable steps for ${chosen!.id}: ${steps.length}`);
  for (const s of steps) console.log(`   ${s.caseId}/${s.stepId}  ·  ${s.title}`);
  assert(steps.every((s) => s.caseId && s.stepId && s.title), "each step needs caseId + stepId + title");

  const step = steps[0];
  console.log(`\n[write] complete_onboarding_step(${step.caseId}/${step.stepId} — "${step.title}")`);
  console.log(`   → ${JSON.stringify(await ob.write(obComplete, { caseId: step.caseId, stepId: step.stepId, title: step.title }))}`);
  const stepsAfter = await ob.read(obSteps, { caseId: chosen!.id });
  console.log(`[HR] actionable steps after: ${stepsAfter.length} (was ${steps.length})`);
  assert(
    !stepsAfter.some((s) => s.caseId === step.caseId && s.stepId === step.stepId),
    "completed step should leave the actionable list",
  );

  // employee: onboarding restricted
  const obEmp = new FrontDoor(config);
  await obEmp.bootstrap("sarah.chen");
  const obEmpCases = await obEmp.read(obCases);
  console.log(`\n[EMPLOYEE] onboarding cases: ${obEmpCases.length} (expect 0 — restricted)`);
  assert(obEmpCases.length === 0, "employee may not view onboarding");

  console.log("\n✅ onboarding slice verified end-to-end against staging");

  // --- Offboarding: toggle a checklist task, proven by the case's progress changing ---
  const offList = config.readTools.find((t) => t.name === "list_offboarding")!;
  const offTasks = config.readTools.find((t) => t.name === "list_offboarding_tasks")!;
  const offToggle = config.writeTools.find((t) => t.name === "toggle_offboarding_task")!;

  const off = new FrontDoor(config);
  await off.bootstrap("priya.nair");
  const offBefore = await off.read(offList);
  console.log(`\n[HR] offboarding cases: ${offBefore.length}`);
  for (const c of offBefore) console.log(`   ${c.id}  ·  ${c.summary}`);
  assert(offBefore.length > 0, "expected a seeded offboarding case");
  assert(offBefore.every((c) => c.id && c.summary), "each case needs id + summary");

  const tasks = await off.read(offTasks);
  console.log(`\n[HR] offboarding tasks: ${tasks.length}`);
  for (const t of tasks.slice(0, 3)) console.log(`   ${t.caseId}/${t.taskId}  ·  ${t.label}`);
  assert(tasks.length > 0, "expected checklist tasks");
  assert(tasks.every((t) => t.caseId && t.taskId), "each task needs caseId + taskId");

  const task = tasks[0];
  const caseBefore = offBefore.find((c) => c.id === task.caseId)!;
  console.log(`\n[write] toggle_offboarding_task(${task.caseId}/${task.taskId})`);
  console.log(`   → ${JSON.stringify(await off.write(offToggle, { caseId: task.caseId, taskId: task.taskId }))}`);
  const offAfter = await off.read(offList);
  const caseAfter = offAfter.find((c) => c.id === task.caseId)!;
  const prog = (s: string) => s.match(/\d+\/\d+ tasks/)?.[0] ?? "?";
  console.log(`   progress ${prog(caseBefore.summary)} → ${prog(caseAfter.summary)}`);
  assert(caseBefore.summary !== caseAfter.summary, "toggling a task must change the case's progress summary");

  // employee restricted
  const offEmp = new FrontDoor(config);
  await offEmp.bootstrap("sarah.chen");
  const offEmpCases = await offEmp.read(offList);
  console.log(`\n[EMPLOYEE] offboarding cases: ${offEmpCases.length} (expect 0 — restricted)`);
  assert(offEmpCases.length === 0, "employee may not view offboarding");

  console.log("\n✅ offboarding slice verified end-to-end against staging");

  // --- Profile: employee submits a sensitive change → HR approves (cross-persona, one workspace) ---
  const submitName = config.writeTools.find((t) => t.name === "submit_legal_name_change")!;
  const listChanges = config.readTools.find((t) => t.name === "list_profile_change_approvals")!;
  const approveChange = config.writeTools.find((t) => t.name === "approve_profile_change")!;

  const pf = new FrontDoor(config);
  await pf.bootstrap("sarah.chen");
  console.log(`\n[EMPLOYEE sarah.chen] submit_legal_name_change("Sarah Jane Chen")`);
  console.log(`   → ${JSON.stringify(await pf.write(submitName, { legalName: "Sarah Jane Chen" }))}`);

  await pf.bootstrap("priya.nair"); // switch persona on the same device/workspace (per-device model)
  const pend = await pf.read(listChanges, { empId: "sarah.chen" });
  console.log(`[HR priya.nair] sarah.chen pending changes: ${pend.length}`);
  for (const c of pend) console.log(`   ${c.empId}/${c.cid}  ·  ${c.summary}`);
  assert(pend.length > 0, "HR should see the submitted sensitive change awaiting approval");
  assert(pend.every((c) => c.empId && c.cid), "each pending change needs empId + cid");

  const chg = pend[0];
  console.log(`\n[write] approve_profile_change(${chg.empId}/${chg.cid})`);
  console.log(`   → ${JSON.stringify(await pf.write(approveChange, { empId: chg.empId, cid: chg.cid }))}`);
  const pendAfter = await pf.read(listChanges, { empId: "sarah.chen" });
  console.log(`[HR] pending after approve: ${pendAfter.length} (was ${pend.length})`);
  assert(!pendAfter.some((c) => c.cid === chg.cid), "approved change should leave the pending list");

  console.log("\n✅ profile slice verified end-to-end against staging");

  // --- Performance (read-only): cycles + reviews, with RBAC scoping ---
  const listCycles = config.readTools.find((t) => t.name === "list_cycles")!;
  const listReviews = config.readTools.find((t) => t.name === "list_reviews")!;

  const pfm = new FrontDoor(config);
  await pfm.bootstrap("priya.nair");
  const cyclesR = await pfm.read(listCycles);
  console.log(`\n[HR] review cycles: ${cyclesR.length}`);
  for (const c of cyclesR) console.log(`   ${c.id}  ·  ${c.summary}`);
  assert(cyclesR.length > 0, "expected seeded review cycles");
  assert(cyclesR.every((c) => c.id && c.summary), "each cycle needs id + summary");

  const reviewsR = await pfm.read(listReviews);
  console.log(`\n[HR] reviews (active cycle): ${reviewsR.length}`);
  for (const r of reviewsR.slice(0, 4)) console.log(`   ${r.cycle}/${r.emp}  ·  ${r.summary}`);
  assert(reviewsR.length > 0, "expected reviews in the active cycle");
  assert(reviewsR.every((r) => r.emp && r.summary), "each review needs emp + summary");

  // employee sees only their own review (RBAC scoping in the service, not just the UI)
  const pfEmp = new FrontDoor(config);
  await pfEmp.bootstrap("sarah.chen");
  const ownReviews = await pfEmp.read(listReviews);
  console.log(`\n[EMPLOYEE sarah.chen] own reviews: ${ownReviews.length}`);
  assert(ownReviews.every((r) => r.emp === "sarah.chen"), "employee must see only their own review row(s)");

  console.log("\n✅ performance slice verified end-to-end against staging");

  // --- Analytics (read-only): dashboard region, RBAC-gated ---
  const getAnalytics = config.readTools.find((t) => t.name === "get_analytics")!;

  const an = new FrontDoor(config);
  await an.bootstrap("priya.nair");
  const dash = await an.read(getAnalytics);
  assert(dash.length === 1, "analytics returns exactly one dashboard region");
  console.log(`\n[HR] analytics summary(head): ${dash[0].summary.slice(0, 160)}…`);
  assert(dash[0].summary.includes("Headcount"), "HR analytics should include the Headcount KPI");

  const anEmp = new FrontDoor(config);
  await anEmp.bootstrap("sarah.chen");
  const dashEmp = await anEmp.read(getAnalytics);
  console.log(`[EMPLOYEE] analytics: ${dashEmp[0].summary.slice(0, 80)}`);
  assert(
    dashEmp[0].summary.includes("available to managers"),
    "employee should get the restricted analytics message, no data",
  );

  console.log("\n✅ analytics slice verified end-to-end against staging");
}

main().catch((e) => {
  console.error(`\n❌ ${(e as Error).message}`);
  process.exit(1);
});
