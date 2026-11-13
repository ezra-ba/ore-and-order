package dev.oreandorder;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static dev.oreandorder.VillagerPackets.Action;

/** A compact non-pausing interaction panel; all buttons request server-validated actions. */
public final class VillagerActionScreen extends Screen {
    private static final String[] ITEMS={"minecraft:raw_iron","minecraft:raw_copper","minecraft:raw_gold","minecraft:coal","minecraft:diamond","minecraft:emerald","minecraft:redstone","minecraft:lapis_lazuli"};
    private static final String[] LABELS={"Raw iron","Raw copper","Raw gold","Coal","Diamond","Emerald","Redstone","Lapis lazuli"};
    private VillagerMenuState state;
    private int tab,resource,ticks,left,top,panelWidth,panelHeight;
    private String quantity="16",order="",localNotice="";
    private final List<Button> mutationButtons=new ArrayList<>();
    private Button mine,send,stop;

    public VillagerActionScreen(VillagerMenuState state) { super(Component.literal("Ore & Order · Villager")); this.state=state; }
    public void update(VillagerMenuState next) {
        boolean rebuild=state.worker()!=next.worker() || state.canManage()!=next.canManage() || state.canRecruit()!=next.canRecruit() || state.canTrade()!=next.canTrade() || state.canUseAi()!=next.canUseAi();
        state=next; localNotice="";
        if(rebuild) { tab=0; rebuildWidgets(); }
    }
    public UUID entity() { return state.entity(); }
    @Override protected void init() {
        panelWidth=Math.min(420,width-16); panelHeight=Math.min(244,height-16);
        left=(width-panelWidth)/2; top=(height-panelHeight)/2;
        int x=left+12,y=top+108,w=panelWidth-24;
        mutationButtons.clear(); mine=null; send=null; stop=null;
        int tabWidth=(w-8)/3;
        for(int i=0;i<3;i++) {
            final int selected=i;
            Button button=button(new String[]{"Actions","Setup","Inventory"}[i],x+i*(tabWidth+4),top+82,tabWidth,() -> {tab=selected;rebuildWidgets();});
            button.active=i!=tab && (i==0 || state.owned());
        }
        if(!state.worker()) {
            Button trade=button("Trade",x,y+4,(w-6)/2,() -> {request(Action.TRADE,""); minecraft.gui.setScreen(null);});
            trade.active=state.canTrade(); trade.setTooltip(Tooltip.create(Component.literal(state.canTrade()?"Open this villager's normal trades.":"No trades are available.")));
            Button recruit=button("Recruit as worker",x+(w+6)/2,y+4,(w-6)/2,() -> request(Action.RECRUIT,""));
            recruit.active=state.canRecruit(); recruit.setTooltip(Tooltip.create(Component.literal("Requires an adult villager, operator permissions and no existing worker.")));
        } else if(tab==0) {
            Button resourceButton=button(LABELS[resource]+" ›",x,y,w-132,() -> {resource=(resource+1)%ITEMS.length;rebuildWidgets();});
            resourceButton.setTooltip(Tooltip.create(Component.literal("Cycle through supported mining resources.")));
            EditBox count=addRenderableWidget(new EditBox(font,x+w-128,y+1,48,18,Component.literal("Quantity")));
            count.setMaxLength(4);count.setValue(quantity);count.setResponder(value -> quantity=value);
            mine=button("Mine",x+w-76,y,76,() -> {
                try {
                    int amount=Integer.parseInt(quantity);
                    if(amount<1 || amount>4096) throw new NumberFormatException();
                    request(Action.MINE,ITEMS[resource]+"|"+amount);
                } catch(NumberFormatException e) {localNotice="Choose a quantity between 1 and 4096.";}
            });
            mine.setTooltip(Tooltip.create(Component.literal("Needs a pickaxe, storage and both area corners. Runs without an API call.")));
            EditBox prompt=addRenderableWidget(new EditBox(font,x,y+27,w-80,18,Component.literal("Worker order")));
            prompt.setMaxLength(500);prompt.setValue(order);prompt.setHint(Component.literal("Get 16 raw iron…"));prompt.setResponder(value -> order=value);
            send=button("Give order",x+w-76,y+26,76,() -> {
                if(order.isBlank()) {localNotice="Type an order first.";return;}
                request(Action.ORDER,order);
            });
            send.setTooltip(Tooltip.create(Component.literal(state.canUseAi()
                    ? "Send this instruction to the server's configured model."
                    : "AI orders require operator permissions. Use Mine for direct orders.")));
            stop=button("Cancel order",x,y+52,112,() -> request(Action.CANCEL,""));
        } else if(tab==1) {
            mutationButtons.add(button("Equip held pickaxe",x,y,(w-6)/2,() -> request(Action.EQUIP,"")));
            mutationButtons.add(button("Return pickaxe",x+(w+6)/2,y,(w-6)/2,() -> request(Action.UNEQUIP,"")));
            int third=(w-8)/3;
            mutationButtons.add(button("Bind storage",x,y+26,third,() -> request(Action.SELECT_STORAGE,"")));
            mutationButtons.add(button("Area corner 1",x+third+4,y+26,third,() -> request(Action.SELECT_FIRST,"")));
            mutationButtons.add(button("Area corner 2",x+2*(third+4),y+26,third,() -> request(Action.SELECT_SECOND,"")));
            for(Button button:mutationButtons) button.setTooltip(Tooltip.create(Component.literal("Setup changes require an idle worker. Block selection returns you to the world.")));
        }
        button("Close",left+panelWidth-78,top+panelHeight-25,66,this::onClose);
        updateButtons();
    }
    private Button button(String label,int x,int y,int width,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(label),button -> action.run()).bounds(x,y,width,20).build());
    }
    private void updateButtons() {
        for(Button button:mutationButtons) button.active=state.canManage() && !state.busy();
        if(mine!=null) mine.active=state.canManage() && !state.busy() && state.ready();
        if(send!=null) send.active=state.canUseAi() && !state.busy() && state.ready();
        if(stop!=null) stop.active=state.canManage() && state.busy();
    }
    private void request(Action action,String value) {
        if(minecraft.getConnection()==null) return;
        if(ClientPlayNetworking.canSend(VillagerPackets.Request.TYPE)) ClientPlayNetworking.send(new VillagerPackets.Request(state.entity(),action,value));
        else localNotice="This server does not support villager actions.";
    }
    @Override public void tick() { updateButtons(); if(++ticks%20==0) request(Action.REFRESH,""); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { request(Action.CLOSE,""); super.onClose(); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics,int mouseX,int mouseY,float delta) {
        graphics.fill(0,0,width,height,0x99090E14);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float delta) {
        int x=left+12,y=top+108,w=panelWidth-24;
        g.fill(left,top,left+panelWidth,top+panelHeight,0xFF17212C);
        g.outline(left,top,panelWidth,panelHeight,0xFF536273);
        g.fill(left,top,left+panelWidth,top+3,0xFFDBB76B);
        g.text(font,"ORE & ORDER",x,top+11,0xFFDBB76B,false);
        g.text(font,font.plainSubstrByWidth(state.name(),w-90),x,top+25,0xFFF3F1E9,false);
        g.text(font,state.worker()?"WORKER":"VILLAGER",left+panelWidth-72,top+13,0xFF94A5B8,false);
        g.fill(x-1,top+41,x+w+1,top+75,0xFF101820);
        wrapped(g,state.status(),x+6,top+46,w-12,2,0xFFCED8E3);
        if(!state.worker()) {
            wrapped(g,"Recruitment turns this villager into your mining worker. Its name, equipment and carried items are kept.",x,y+34,w,3,0xFF9EAFBF);
        } else if(tab==0) {
            wrapped(g,state.ready()?"Ore drops only · smelting comes later":"Open Setup: equip a pickaxe, bind storage and mark an area.",x+120,y+54,w-120,2,0xFF9EAFBF);
        } else if(tab==1) {
            g.text(font,font.plainSubstrByWidth("Storage: "+state.storage(),w),x,y+54,0xFFCED8E3,false);
            g.text(font,font.plainSubstrByWidth("Area: "+state.area(),w),x,y+66,0xFF9EAFBF,false);
        } else {
            int slotSize=Math.min(38,(w-4)/8);
            for(int i=0;i<8;i++) {
                int sx=x+i*slotSize,sy=y+3;
                g.fill(sx,sy,sx+slotSize-3,sy+28,0xFF0D151D);
                if(i<state.inventory().size()) {
                    var info=state.inventory().get(i);
                    if(info.count()>0 && minecraft.level!=null) {
                        var item=minecraft.level.registryAccess().lookupOrThrow(Registries.ITEM)
                            .get(ResourceKey.create(Registries.ITEM,Identifier.parse(info.item())));
                        if(item.isPresent()) g.item(new ItemStack(item.get()),sx+4,sy+3);
                        g.text(font,Integer.toString(info.count()),sx+slotSize-5-font.width(Integer.toString(info.count())),sy+18,0xFFF3F1E9);
                        if(mouseX>=sx && mouseX<sx+slotSize-3 && mouseY>=sy && mouseY<sy+28)
                            g.setTooltipForNextFrame(Component.literal(info.name()+" × "+info.count()),mouseX,mouseY);
                    }
                }
            }
            g.text(font,font.plainSubstrByWidth("Tool: "+state.tool(),w),x,y+42,0xFFCED8E3,false);
            wrapped(g,"Carried items stay with the worker when a job is cancelled.",x,y+57,w,2,0xFF9EAFBF);
        }
        String notice=localNotice.isEmpty()?state.notice():localNotice;
        g.text(font,font.plainSubstrByWidth(notice,panelWidth-100),x,top+panelHeight-19,0xFFDBB76B,false);
        super.extractRenderState(g,mouseX,mouseY,delta);
    }
    private void wrapped(GuiGraphicsExtractor g,String text,int x,int y,int width,int maxLines,int color) {
        var lines=font.split(Component.literal(text),width);
        for(int i=0;i<Math.min(maxLines,lines.size());i++) g.text(font,lines.get(i),x,y+i*10,color,false);
    }
}
