import java.io.File;
import java.util.*;
import org.jf.dexlib2.*;
import org.jf.dexlib2.analysis.*;
import org.jf.dexlib2.builder.*;
import org.jf.dexlib2.builder.instruction.*;
import org.jf.dexlib2.dexbacked.*;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.instruction.*;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.immutable.*;
import org.jf.dexlib2.immutable.instruction.*;
import org.jf.dexlib2.immutable.reference.*;

public final class PatchPolicy {
    public static void main(String[] args) throws Exception {
        Opcodes op=Opcodes.forApi(17);
        DexBackedOdexFile original=(DexBackedOdexFile)DexFileFactory.loadDexFile(args[0],op);
        ClassPathResolver resolver=new ClassPathResolver(Collections.singletonList(args[1]),Collections.<String>emptyList(),original);
        ClassPath cp=new ClassPath(resolver.getResolvedClassProviders(),false,ClassPath.NOT_ART);
        InlineMethodResolver inline=InlineMethodResolver.createInlineMethodResolver(original.getOdexVersion());
        List<ClassDef> before=new ArrayList<>(),after=new ArrayList<>(); int hooks=0, analyzed=0;
        for(ClassDef cls:original.getClasses()) {
            List<Method> bm=new ArrayList<>(),am=new ArrayList<>();
            for(Method method:cls.getMethods()) {
                MethodImplementation impl=method.getImplementation();
                if(impl!=null) {
                    boolean optimized=false;for(Instruction ins:impl.getInstructions())optimized|=ins.getOpcode().odexOnly();
                    if(optimized) {
                        MethodAnalyzer ma=new MethodAnalyzer(cp,method,inline,false);
                        if(ma.getAnalysisException()!=null)throw ma.getAnalysisException();
                        List<Instruction> clean=new ArrayList<>();int originalSize=0,cleanSize=0;
                        for(Instruction ins:impl.getInstructions())originalSize+=ins.getCodeUnits();
                        int index=0;
                        for(Instruction ins:ma.getInstructions()) {
                            if(ins instanceof UnresolvedOdexInstruction) {
                                UnresolvedOdexInstruction unresolved=(UnresolvedOdexInstruction)ins;
                                RegisterType type=ma.getAnalyzedInstructions().get(index).getPreInstructionRegisterType(unresolved.objectRegisterNum);
                                if(type.category!=RegisterType.NULL || args.length==5)throw new IllegalStateException("Unresolved non-null/patch instruction "+method+" "+type);
                                // A proven-null receiver always throws NPE. Preserve its try region and code offsets.
                                clean.add(new ImmutableInstruction11x(Opcode.THROW,unresolved.objectRegisterNum));
                                for(int n=1;n<ins.getCodeUnits();n++)clean.add(new ImmutableInstruction10x(Opcode.NOP));
                                cleanSize+=ins.getCodeUnits();index++;
                                System.out.println("PROVEN_NULL_RECEIVER "+method+" instruction="+(index-1));
                                continue;
                            }
                            if(ins.getOpcode().odexOnly())throw new IllegalStateException("Unresolved "+method);
                            clean.add(ins);cleanSize+=ins.getCodeUnits();
                            index++;
                        }
                        if(originalSize!=cleanSize)throw new IllegalStateException("Offsets changed "+method);
                        impl=new ImmutableMethodImplementation(impl.getRegisterCount(),clean,impl.getTryBlocks(),impl.getDebugItems()); analyzed++;
                    }
                }
                bm.add(copy(method,impl));
                if(args.length==5 && cls.getType().equals("Lcom/android/internal/policy/impl/PhoneWindowManager;") && method.getName().equals("layoutWindowLw")) {
                    MutableMethodImplementation mutable=new MutableMethodImplementation(impl);
                    for(int i=mutable.getInstructions().size()-1;i>=0;i--) {
                        Instruction ins=mutable.getInstructions().get(i);
                        if(ins.getOpcode()!=Opcode.INVOKE_INTERFACE)continue;
                        Reference ref=((ReferenceInstruction)ins).getReference();
                        if(!(ref instanceof MethodReference)||!((MethodReference)ref).getName().equals("computeFrameLw"))continue;
                        FiveRegisterInstruction regs=(FiveRegisterInstruction)ins;
                        if(regs.getRegisterCount()!=5)throw new IllegalStateException("Bad register count");
                        // Replace at the original label so every incoming branch goes through the helper.
                        mutable.replaceInstruction(i,new BuilderInstruction35c(Opcode.INVOKE_STATIC,5,regs.getRegisterC(),regs.getRegisterD(),regs.getRegisterE(),regs.getRegisterF(),regs.getRegisterG(),
                                new ImmutableMethodReference("Ldev/stinger/split/policy/SplitPolicy;","constrain",Arrays.asList("Ljava/lang/Object;","Landroid/graphics/Rect;","Landroid/graphics/Rect;","Landroid/graphics/Rect;","Landroid/graphics/Rect;"),"V")));
                        mutable.addInstruction(i+1,new BuilderInstruction10x(Opcode.NOP));
                        mutable.addInstruction(i+2,new BuilderInstruction35c(Opcode.INVOKE_INTERFACE,5,regs.getRegisterC(),regs.getRegisterD(),regs.getRegisterE(),regs.getRegisterF(),regs.getRegisterG(),(MethodReference)ref));
                        hooks++;
                    }
                    impl=mutable;
                }
                am.add(copy(method,impl));
            }
            before.add(copy(cls,bm));after.add(copy(cls,am));
        }
        DexFileFactory.writeDexFile(args[2],new ImmutableDexFile(op,before));
        if(args.length==5) {
            if(hooks!=1)throw new IllegalStateException("Unexpected hooks "+hooks);
            for(ClassDef cls:DexFileFactory.loadDexFile(new File(args[3]),op).getClasses()) {
                if(!cls.getType().startsWith("Ldev/stinger/split/policy/"))throw new IllegalStateException("Helper namespace");after.add(cls);
            }
            DexFileFactory.writeDexFile(args[4],new ImmutableDexFile(op,after));
        }
        System.out.println("classes="+before.size()+" analyzed="+analyzed+" hooks="+hooks+" unresolved=0");
    }
    static Method copy(Method m,MethodImplementation impl){return new ImmutableMethod(m.getDefiningClass(),m.getName(),m.getParameters(),m.getReturnType(),m.getAccessFlags(),m.getAnnotations(),impl);}
    static ClassDef copy(ClassDef c,List<Method> methods){return new ImmutableClassDef(c.getType(),c.getAccessFlags(),c.getSuperclass(),c.getInterfaces(),c.getSourceFile(),c.getAnnotations(),c.getFields(),methods);}
}
