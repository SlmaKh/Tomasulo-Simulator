import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

public class Tomasulo {
	static int clock;
	static ArrayList<Instruction> instructions;
	static Memory mem;
	static AddReservationStation addRS;
	static MulReservationStation mulRS;
	static LoadBuffers loadBuffers;
	static StoreBuffers storeBuffers;
	static RegisterFile registerFile;
	static int[] intRegFile;
	static Queue<tagValue> CDB;

	static int addCycles = 2;
	static int mulCycles = 10;
	static int loadCycles = 2;
	static int storeCycles = 2;

	static int globalTag = 1;
	static ArrayList<TracingEntry> tracingInstructions;
	static class TracingEntry{
		Instruction inst;
		int issue;
		int startExecution,endExecution;
		int writeResult;
		public TracingEntry(Instruction inst) {
			this.inst=inst;
			issue=startExecution=endExecution=writeResult=-1;

		}

	}
	static void printTracingInstructions() {
		StringBuilder sb=new StringBuilder();
		String s="------------------------------------------------------------------------------------------------------------------\n";
		sb.append("the Instruction tracing\n");
		sb.append(s);
		sb.append(String.format("%20s %15s %10s %10s %10s %10s %10s %10s","Instruction type","destination","j","k","issue","start exec","end exec","write result")+"\n");
		sb.append(s);
		for(TracingEntry e:tracingInstructions) {
			sb.append(String.format("%20s %15s %10s %10s %10s %10s %10s %10s",e.inst.getType(),e.inst.dest,e.inst.j,e.inst.k,e.issue,e.startExecution,
					e.endExecution,e.writeResult)+"\n");
			sb.append(s);

		}
		sb.append(s);
		System.out.println(sb);

	}
	static class tagValue {
		int tag;
		double val;
		int instID;
		public tagValue(int tag, double val,int instID) {
			this.tag = tag;
			this.val = val;
			this.instID=instID;
		}

	}

	static void init() throws IOException {
		instructions = new ArrayList<>();
		tracingInstructions=new ArrayList<>();
		loadInstructions();
		for(Instruction i :instructions)
			tracingInstructions.add(new TracingEntry(i));

		clock = 1;
		mem = new Memory(1024);
		addRS = new AddReservationStation(3);
		mulRS = new MulReservationStation(2);
		loadBuffers = new LoadBuffers(3);
		storeBuffers = new StoreBuffers(3);
		registerFile = new RegisterFile(32);
		CDB = new LinkedList();
		intRegFile = new int[32];
		for (int i = 0; i < intRegFile.length; i++)
			intRegFile[i] = i;

	}

	static void loadInstructions() throws IOException {
		Scanner sc = new Scanner(System.in);

		BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("enter number of instructions ");
		int n = sc.nextInt();
		for(int i=0;i<n;i++) {
			//System.out.println("please enter an instruction");
			String s = bf.readLine();
			Instruction inst=new Instruction(s);
			inst.id=i;
			instructions.add(inst);
		}
	}

	public static boolean addAdd(Instruction inst) {
		int loc = addRS.isFree();
		if (loc < 0)
			return false;
		double vj = registerFile.registerFile[inst.j].value;
		double vk = registerFile.registerFile[inst.k].value;
		int qj = registerFile.registerFile[inst.j].Qi;
		int qk = registerFile.registerFile[inst.k].Qi;  //we are bahayemmmm 

		registerFile.registerFile[inst.dest].Qi = globalTag;

		ReservationStation rs = new ReservationStation(globalTag++, 1, inst.getTypeVal(), vj, vk, qj, qk, 0, addCycles);
		//hereee
		rs.instID = inst.id;
		addRS.addRS[loc] = rs;

		return true;
	}

	public static boolean addMul(Instruction inst) {
		int loc = mulRS.isFree();
		if (loc < 0)
			return false;
		double vj = registerFile.registerFile[inst.j].value;
		double vk = registerFile.registerFile[inst.k].value;
		int qj = registerFile.registerFile[inst.j].Qi;
		int qk = registerFile.registerFile[inst.k].Qi;

		registerFile.registerFile[inst.dest].Qi = globalTag;

		ReservationStation rs = new ReservationStation(globalTag++, 1, inst.getTypeVal(), vj, vk, qj, qk, 0, mulCycles);
		//hereee
		rs.instID = inst.id;
		mulRS.mulRS[loc] = rs;

		return true;

	}


	public static boolean addLoad(Instruction inst) {
		int loc = loadBuffers.isFree();
		if (loc < 0)
			return false;

		registerFile.registerFile[inst.dest].Qi = globalTag;

		LoadBuffers.BufferEntry be = new LoadBuffers.BufferEntry(globalTag++, 1, inst.j + intRegFile[inst.k],
				loadCycles);
		//hereeee
		be.instID = inst.id;

		loadBuffers.loadBuffers[loc] = be;
		return true;

	}


	public static boolean addStore(Instruction inst) {
		int loc = storeBuffers.isFree();
		if (loc < 0)
			return false;
		int address = inst.j + intRegFile[inst.k];

		mem.data[address % mem.size].Qi = globalTag;

		StoreBuffers.BufferEntry be = new StoreBuffers.BufferEntry(globalTag++, 1, address,
				registerFile.registerFile[inst.dest].value, registerFile.registerFile[inst.dest].Qi, storeCycles);

		//heree
		be.instID = inst.id;
		storeBuffers.storeBuffers[loc] = be;
		return true;

	}

	static boolean issueInstruction() {
		if (instructions.size() == 0)
			return false;

		Instruction inst = instructions.get(0);
		boolean remove = false;
		Instruction.Type type = inst.type;
		if (type == Instruction.Type.add) {
			remove |= addAdd(inst);
		}
		if (type == Instruction.Type.mul) {
			remove |= addMul(inst);
		}

		if (type == Instruction.Type.load) {
			remove |= addLoad(inst);
		}

		if (type == Instruction.Type.store) {
			remove |= addStore(inst);
		}
		//	if(type==Instruction.Type.branch)
		//	{
		//			
		//	}

		if (remove) {
			int remainingInstruction=instructions.size();
			int idx=tracingInstructions.size()-remainingInstruction;
			tracingInstructions.get(idx).issue=clock;
			instructions.remove(0);
		}
		return remove;
	}

	static void publish(tagValue tagValue) {
		int tag = tagValue.tag;
		double val = tagValue.val;
		for (int i = 0; i < registerFile.size; i++)
			if (registerFile.registerFile[i].Qi == tag) {
				registerFile.registerFile[i].Qi = 0;
				registerFile.registerFile[i].value = val;
				tracingInstructions.get(tagValue.instID).writeResult=clock;
			}
		for (int i = 0; i < addRS.size; i++) {
			if (addRS.addRS[i].qj == tag) {
				addRS.addRS[i].qj = 0;
				addRS.addRS[i].vj = val;
			}
			if (addRS.addRS[i].qk == tag) {
				addRS.addRS[i].qk = 0;
				addRS.addRS[i].vk = val;
			}
		}

		for (int i = 0; i < mulRS.size; i++) {
			if (mulRS.mulRS[i].qj == tag) {
				mulRS.mulRS[i].qj = 0;
				mulRS.mulRS[i].vj = val;
			}
			if (mulRS.mulRS[i].qk == tag) {
				mulRS.mulRS[i].qk = 0;
				mulRS.mulRS[i].vk = val;
			}
		}

		for (int i = 0; i < storeBuffers.size; i++)
			if (storeBuffers.storeBuffers[i].Qi == tag) {
				storeBuffers.storeBuffers[i].Qi = 0;
				storeBuffers.storeBuffers[i].value = val;
			}

		for (int i = 0; i < mem.size; i++)
			if (mem.data[i].Qi == tag) {
				mem.data[i].Qi = 0;
				mem.data[i].value = val;
				tracingInstructions.get(tagValue.instID).writeResult=clock;
			}

	}

	static void handleAddUnit() {
		ArrayList<Integer> locations = addRS.nextInstruction();
		for(int loc : locations) {
			ReservationStation rs = addRS.addRS[loc];
			int instID = rs.instID;
			if (rs.time > 1) {
				//if it was issued in that same clock cycle
				if(tracingInstructions.get(instID).issue == clock)
					continue;


				if(rs.time == addCycles) {
					tracingInstructions.get(instID).startExecution = clock;
				}
				rs.time--;
			} else {
				rs.busy = 0;
				rs.time--;

				tracingInstructions.get(instID).endExecution = clock;
				//	tracingInstructions.get(instID).writeResult = clock + 1;

				double result = rs.vj + rs.vk;
				CDB.add(new tagValue(rs.tag, result,instID));
			}
		}

	}

	static void handleMulUnit() {

		//		int loc = mulRS.nextInstruction();
		ArrayList<Integer> locations=mulRS.nextInstruction();
		for(int loc:locations) {
			//		if (loc < 0)
			//			return;
			ReservationStation rs = mulRS.mulRS[loc];
			int instID = rs.instID;
			if (rs.time > 1) {
				//hereee
				if(tracingInstructions.get(instID).issue == clock)
					continue;

				if(rs.time == mulCycles) {
					tracingInstructions.get(instID).startExecution = clock;
				}
				rs.time--;
			} else {
				//hereeee			
				tracingInstructions.get(instID).endExecution = clock;
				//	tracingInstructions.get(instID).writeResult = clock + 1;
				rs.time--;

				rs.busy = 0;
				double result = rs.vj * rs.vk;
				CDB.add(new tagValue(rs.tag, result,instID));
			}
		}
	}

	//	static void handleLoad() {
	//		ArrayList<Integer>locations = loadBuffers.nextInstruction();
	//		for(int locLoad : locations) {
	//			LoadBuffers.BufferEntry be = loadBuffers.loadBuffers[locLoad];
	//			int instID = be.instID;
	//			if (be.time > 1) {
	//				if(be.time == loadCycles) {
	//					tracingInstructions.get(instID).startExecution = clock;
	//				}
	//				be.time--;
	//			} else {
	//				tracingInstructions.get(instID).endExecution = clock;
	//		//		tracingInstructions.get(instID).writeResult = clock + 1;
	//				be.time--;
	//				
	//				be.busy = 0;
	//				CDB.add(new tagValue(be.tag, mem.data[be.address % mem.size].value,instID));
	//			}
	//
	//			
	//		}
	//	}

	//	static void handleStore() {
	//		ArrayList<Integer>locations = storeBuffers.nextInstruction();
	//		for(int locLoad : locations) {
	//			StoreBuffers.BufferEntry be = storeBuffers.storeBuffers[locLoad];
	//			int instID = be.instID;
	//			if (be.time > 1) {
	//				if(be.time == storeCycles) {
	//					tracingInstructions.get(instID).startExecution = clock;
	//				}
	//				be.time--;
	//			} else {
	//				tracingInstructions.get(instID).endExecution = clock;
	//			//	tracingInstructions.get(instID).writeResult = clock + 1;
	//				be.time--;
	//				
	//				be.busy = 0;
	//				CDB.add(new tagValue(be.tag, mem.data[be.address % mem.size].value,instID));
	//			}
	//		}
	//	}


	//TODO 
	//Ask Dr. about this and remember to handle starting the execute after the issue after knowing what to do.
	static void handleMem() {
		int locLoad = loadBuffers.nextInstruction();
		int locStore = storeBuffers.nextInstruction();
		if (locLoad == -1)
			locLoad = Integer.MAX_VALUE;
		if (locStore == -1)
			locStore = Integer.MAX_VALUE;
		if (locLoad < locStore ) {
			// load inst
			LoadBuffers.BufferEntry be = loadBuffers.loadBuffers[locLoad];
			int instID = be.instID;
			if(tracingInstructions.get(instID).issue != clock) {
				if (be.time > 1) {
					//hereeeee
					//this doesn't work hereee, you have to change it in next Instruction function
					//				if(tracingInstructions.get(instID).issue == clock)
					//					return;

					if(be.time == loadCycles) {
						tracingInstructions.get(instID).startExecution = clock;
					}
					be.time--;
				} else {
					//hereeeee
					tracingInstructions.get(instID).endExecution = clock;
//					tracingInstructions.get(instID).writeResult = clock + 1;
					be.time --;
					be.busy = 0;
					CDB.add(new tagValue(be.tag, mem.data[be.address % mem.size].value, instID));
				}
			}

		} 
		if (locStore < locLoad) {
			System.out.println(" entered store "+locStore);

			StoreBuffers.BufferEntry be = storeBuffers.storeBuffers[locStore];
			int instID = be.instID;
			if(tracingInstructions.get(instID).issue != clock) {

				if (be.time > 1) {
					if(be.time == loadCycles) {
						System.out.println(" assigned start execution");
						tracingInstructions.get(instID).startExecution = clock;
					}

					be.time--;
				} else {
					tracingInstructions.get(instID).endExecution = clock;
//					tracingInstructions.get(instID).writeResult = clock + 1;

					be.busy = 0;
					be.time --;
					mem.data[be.address % mem.size].value = be.value;

					CDB.add(new tagValue(be.tag, be.value, instID));
				}

			}
		}
	}

	static void nextCycle() {

		//exec add unit
		handleAddUnit();

		//exec mul unit
		handleMulUnit();

		// exec mem unit
		handleMem();
		//		handleLoad();
		//
		//		handleStore();



		issueInstruction();

		if (!CDB.isEmpty()) {
			int id=CDB.peek().instID;
			//This condition is to enforce that the publishing of the result happens after the end of execution. 
			if(clock>tracingInstructions.get(id).endExecution)
				publish(CDB.poll());

		}

		clock++;
	}

	public static void main(String[] args) throws IOException {
		Scanner sc = new Scanner(System.in);
		BufferedReader bf = new BufferedReader(new InputStreamReader(System.in));
		init();

		String s;
		//hereeeere
		System.out.println("The whole system status initially");
		display();

		System.out.println("press n for next Cycle");
		while ((s =bf.readLine()).equals("n")) {
			System.out.println("Clock Cycle: " + clock);
			nextCycle();
			display();

			System.out.println("press n for next Cycle");
			//s = bf.readLine();
		}

	}

	public static void display() {
		printTracingInstructions();
		//		System.out.println(registerFile.toString());
		//		System.out.println(addRS.toString());
		//		System.out.println(mulRS);
		System.out.println(loadBuffers);
		System.out.println(storeBuffers);

	}
}







/*
l.d 6 32 2
l.d 2 44 3
mul 0 2 4
add 8 2 6
mul 10 0 6
add 6 8 2
 */


/*
mul 10 1 6
s.d 10 0 10
l.d 5 0 10






 */