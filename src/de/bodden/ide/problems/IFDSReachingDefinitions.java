package de.bodden.ide.problems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import de.bodden.ide.FlowFunction;
import de.bodden.ide.FlowFunctions;
import de.bodden.ide.InterproceduralCFG;
import de.bodden.ide.flowfunc.Identity;
import de.bodden.ide.flowfunc.KillAll;
import de.bodden.ide.template.DefaultIFDSTabulationProblem;

import soot.EquivalentValue;
import soot.Local;
import soot.NullType;
import soot.Scene;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.DefinitionStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JimpleLocal;
import soot.toolkits.scalar.Pair;

public class IFDSReachingDefinitions extends DefaultIFDSTabulationProblem<Pair<Value, Set<DefinitionStmt>>,InterproceduralCFG<Unit, SootMethod>> {
	public IFDSReachingDefinitions(InterproceduralCFG<Unit, SootMethod> icfg) {
		super(icfg);
	}
	
	@Override
	public FlowFunctions<Unit, Pair<Value, Set<DefinitionStmt>>, SootMethod> createFlowFunctionsFactory() {
		return new FlowFunctions<Unit, Pair<Value, Set<DefinitionStmt>>, SootMethod>() {

			@Override
			public FlowFunction<Pair<Value, Set<DefinitionStmt>>> getNormalFlowFunction(final Unit curr, Unit succ) {
				if (curr instanceof DefinitionStmt) {
					final DefinitionStmt assignment = (DefinitionStmt) curr;

					return new FlowFunction<Pair<Value, Set<DefinitionStmt>>>() {
						@Override
						public Set<Pair<Value, Set<DefinitionStmt>>> computeTargets(Pair<Value, Set<DefinitionStmt>> source) {
							if (source != zeroValue()) {
								if (source.getO1().equivTo(assignment.getLeftOp())) {
									return Collections.emptySet();
								}
								return Collections.singleton(source);
							} else {
								LinkedHashSet<Pair<Value, Set<DefinitionStmt>>> res = new LinkedHashSet<Pair<Value, Set<DefinitionStmt>>>();
								 res.add(new Pair<Value, Set<DefinitionStmt>>(assignment.getLeftOp(),
													Collections.<DefinitionStmt> singleton(assignment)));
								return res;
							}
						}
					};
				}

				return Identity.v();
			}

			@Override
			public FlowFunction<Pair<Value, Set<DefinitionStmt>>> getCallFlowFunction(Unit callStmt,
					final SootMethod destinationMethod) {
				Stmt stmt = (Stmt) callStmt;
				InvokeExpr invokeExpr = stmt.getInvokeExpr();
				final List<Value> args = invokeExpr.getArgs();

				final List<Local> localArguments = new ArrayList<Local>(args.size());
				for (Value value : args) {
					if (value instanceof Local)
						localArguments.add((Local) value);
					else
						localArguments.add(null);
				}

				return new FlowFunction<Pair<Value, Set<DefinitionStmt>>>() {

					@Override
					public Set<Pair<Value, Set<DefinitionStmt>>> computeTargets(Pair<Value, Set<DefinitionStmt>> source) {
						if(localArguments.contains(source.getO1())) {
							int paramIndex = args.indexOf(source.getO1());
							Pair<Value, Set<DefinitionStmt>> pair = new Pair<Value, Set<DefinitionStmt>>(
									new EquivalentValue(Jimple.v().newParameterRef(destinationMethod.getParameterType(paramIndex), paramIndex)),
									source.getO2());
							return Collections.singleton(pair);
						}

						return Collections.emptySet();
					}
				};
			}

			@Override
			public FlowFunction<Pair<Value, Set<DefinitionStmt>>> getReturnFlowFunction(final Unit callSite,
					SootMethod calleeMethod, final Unit exitStmt, Unit returnSite) {
				if (!(callSite instanceof DefinitionStmt))
					return KillAll.v();

				if (exitStmt instanceof ReturnVoidStmt)
					return KillAll.v();

				return new FlowFunction<Pair<Value, Set<DefinitionStmt>>>() {

					@Override
					public Set<Pair<Value, Set<DefinitionStmt>>> computeTargets(Pair<Value, Set<DefinitionStmt>> source) {
						if(exitStmt instanceof ReturnStmt) {
							ReturnStmt returnStmt = (ReturnStmt) exitStmt;
							if (returnStmt.getOp().equivTo(source.getO1())) {
								DefinitionStmt definitionStmt = (DefinitionStmt) callSite;
								Pair<Value, Set<DefinitionStmt>> pair = new Pair<Value, Set<DefinitionStmt>>(
										definitionStmt.getLeftOp(), source.getO2());
								return Collections.singleton(pair);
							}
						}
						return Collections.emptySet();
					}
				};
			}

			@Override
			public FlowFunction<Pair<Value, Set<DefinitionStmt>>> getCallToReturnFlowFunction(Unit callSite, Unit returnSite) {
				if (!(callSite instanceof DefinitionStmt))
					return Identity.v();
				
				final DefinitionStmt definitionStmt = (DefinitionStmt) callSite;
				return new FlowFunction<Pair<Value, Set<DefinitionStmt>>>() {

					@Override
					public Set<Pair<Value, Set<DefinitionStmt>>> computeTargets(Pair<Value, Set<DefinitionStmt>> source) {
						if(source.getO1().equivTo(definitionStmt.getLeftOp())) {
							return Collections.emptySet();
						} else {
							return Collections.singleton(source);
						}
					}
				};
			}
		};
	}

	@Override
	public Set<Unit> initialSeeds() {
		return Collections.singleton(Scene.v().getMainMethod().getActiveBody().getUnits().getFirst());
	}

	public Pair<Value, Set<DefinitionStmt>> createZeroValue() {
		return new Pair<Value, Set<DefinitionStmt>>(new JimpleLocal("<<zero>>", NullType.v()), Collections.<DefinitionStmt> emptySet());
	}

}