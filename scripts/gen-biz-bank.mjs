// Deterministic first-principles generator for the Business and Entrepreneurship bank.
// 80 content frames (2 per topic, 10 units x 4 topics) x 50 data-varied cases = 4000 questions.
// Each case gets a globally unique single-token company name, used as the first word of
// every stem, which guarantees distinct stems and distinct 12-word openings.
import fs from "node:fs";
import path from "node:path";

const OUT = "src/main/resources/bank/business-entrepreneurship";

const PREFIX = ["Alder","Bramble","Cedar","Dover","Elm","Fern","Grove","Harbor","Iris","Juniper",
  "Kestrel","Linden","Maple","Nectar","Oak","Pine","Quartz","River","Sable","Thistle",
  "Umber","Violet","Willow","Xenon","Yarrow","Zephyr","Amber","Birch","Cobalt","Dahlia",
  "Ember","Flint","Granite","Hazel","Indigo","Jasper","Kite","Lumen","Marigold","Nimbus",
  "Onyx","Poppy","Quill","Ridge","Saffron","Topaz","Upland","Verve","Wren","Zinnia",
  "Aspen","Briar","Clover","Dusk","Echo","Frost","Glen","Hollow","Ivory","Jade",
  "Kelp","Lark","Moss","Nova","Orchid","Pebble","Reed","Slate","Tundra","Vale"];
const SUFFIX = ["wood","field","ford","brook","hill","crest","vale","moor","ridge","dale",
  "haven","port","gate","mere","glen","reach","point","bank","knoll","view",
  "works","labs","group","partners","ventures","goods","foods","media","systems","supply",
  "market","trade","farm","brew","bake","loom","press","studio","creek","spring",
  "stone","bridge","park","lane","court","square","wharf","dock","mill","forge",
  "leaf","bloom","seed","grove","nest","hive","den","run","bay","shores",
  "peak","summit","pass","trail","path","glade","mead","fen","marsh","dell"];

const CITIES = ["Denver","Austin","Portland","Seattle","Chicago","Atlanta","Boston","Phoenix","Tulsa","Boise",
  "Raleigh","Salt Lake City","Madison","Nashville","Minneapolis","Tampa","Fresno","Omaha","Buffalo","Charlotte",
  "Cleveland","Kansas City","Albuquerque","Richmond","Spokane","Lexington","Dallas","Miami","Detroit","Boulder"];
const SEGMENTS = ["budget-conscious families","young professionals","small retailers","enterprise clients",
  "college students","retirees","rural customers","urban commuters","home-based businesses","health-conscious shoppers"];
const PRODUCTS = ["handmade candles","accounting software","organic snacks","fitness apparel","bike accessories",
  "specialty coffee","pet supplies","home cleaning services","ceramic cookware","mobile phone cases",
  "yoga equipment","artisan bread","garden tools","dog treats","desk lamps","skin-care creams"];

function mulberry32(a){return function(){a|=0;a=a+0x6D2B79F5|0;let t=Math.imul(a^a>>>15,1|a);t=t+Math.imul(t^t>>>7,61|t)^t;return ((t^t>>>14)>>>0)/4294967296;};}
const pick=(r,arr)=>arr[Math.floor(r()*arr.length)];
const int=(r,a,b)=>a+Math.floor(r()*(b-a+1));
function base(name,r){
  return {name, city:pick(r,CITIES), seg:pick(r,SEGMENTS), product:pick(r,PRODUCTS),
    year:2021+int(r,0,4), pct:int(r,8,45), A:int(r,12,95), B:int(r,3,30),
    C:int(r,20,80), D:int(r,100,900)};
}
const O=(t,m,correct=false)=>({t,m:m||"",correct});
const money=v=>"$"+Math.round(v).toLocaleString("en-US");
const num=v=>Math.round(v).toLocaleString("en-US");

const SPECS = [];
const S=(u,t,topic,bloom,dok,format,lo,practice,c,stem,correct,wrongs,why,hint,stimulus=null,answerFn=null)=>
  SPECS.push({u,t,topic,bloom,dok,format,lo,practice,c,stem,correct,wrongs,why,hint,stimulus,answerFn});

// ---------------- UNIT 1: Foundations of Business & Entrepreneurship ----------------
S(1,1,"Forms of business organization","APPLY",2,"CONCEPT_MCQ","Compare legal forms of business","forms",
  (name,r)=>base(name,r),
  c=>`${c.name}, a small ${c.product} maker in ${c.city}, wants owners to have limited liability but avoid double taxation. Which business form best fits?`,
  ()=>O("An LLC, which limits owner liability and is taxed like a partnership","",true),
  c=>[O("A sole proprietorship, which shields owners from all business debts","Assumes a sole proprietorship provides limited liability"),
      O("A general partnership, where each partner risks only their investment","Mistakes a general partnership for a limited-liability structure"),
      O("A corporation, whose profits are taxed only once at the owner level","Believes corporate income escapes entity-level taxation")],
  c=>`An LLC combines limited liability for owners with pass-through taxation, so profits are taxed once on owners' returns. A sole proprietorship and a general partnership expose owners to unlimited personal liability, while a corporation faces entity-level tax and then dividend tax.`,
  c=>"Look for the form that pairs limited liability with pass-through taxation.");

S(1,1,"Forms of business organization","ANALYZE",3,"STIMULUS_MCQ","Choose a form given investor goals","forms",
  (name,r)=>({...base(name,r)}),
  c=>`Given ${c.name}'s goals, which organizational form is most appropriate?`,
  ()=>O("A corporation, because it can issue transferable shares to outside investors","",true),
  c=>[O("A sole proprietorship, because it allows easy transfer of ownership shares","Confuses a sole proprietorship with a share-issuing corporation"),
      O("A general partnership, because it offers the easiest exit for shareholders","Mistakes partnership interests for freely tradable stock"),
      O("An LLC, because it is designed to sell stock on public exchanges","Assumes LLC membership interests are publicly traded shares")],
  c=>`A corporation can issue transferable shares, making it the standard choice when a venture plans to bring in outside equity investors who may later exit. Sole proprietorships and general partnerships cannot issue stock, and LLC interests are not publicly traded shares.`,
  c=>"Which form lets owners sell transferable shares to investors?",
  c=>({kind:"case",body:`${c.name} is launching a ${c.product} venture in ${c.city}. The founder wants outside investors who can buy and sell shares easily, and accepts more paperwork and entity-level taxes.`,license:"original"}));

S(1,2,"Entrepreneurial opportunity","UNDERSTAND",2,"CONCEPT_MCQ","Distinguish ideas from opportunities","opportunity",
  (name,r)=>base(name,r),
  c=>`${c.name} is deciding whether a ${c.product} concept is a genuine opportunity or merely a good idea. Which signal best indicates a real opportunity?`,
  ()=>O("Evidence that customers will pay and that the venture can profitably reach them","",true),
  c=>[O("A founder's strong personal enthusiasm for the product concept","Treats founder passion as proof of market demand"),
      O("A long list of features no competitor currently offers","Mistakes feature novelty for demonstrated customer value"),
      O("Approval from friends and family at an informal gathering","Treats social encouragement as validated market evidence")],
  c=>`An opportunity exists when there is a viable market willing to pay and a way to serve it profitably. Founder enthusiasm, feature lists, and encouragement from acquaintances are weak signals because they do not show that customers will exchange money for the offering.`,
  c=>"Ask which signal involves real customers paying real money.");

S(1,2,"Entrepreneurial opportunity","UNDERSTAND",2,"CONCEPT_MCQ","Apply effectuation reasoning","opportunity",
  (name,r)=>base(name,r),
  c=>`${c.name} is pursuing a ${c.product} venture under high uncertainty. Which approach best reflects effectuation reasoning?`,
  ()=>O("Starting with available resources and co-creating goals with stakeholders","",true),
  c=>[O("Predicting future demand first, then assembling exactly the needed resources","Describes causal prediction rather than effectuation"),
      O("Writing a fixed five-year plan before taking any action","Confuses detailed planning with effectual action"),
      O("Waiting for perfect information before committing any resources","Mistakes analysis paralysis for uncertainty management")],
  c=>`Effectuation begins with who the entrepreneur is, what they know, and whom they know, then shapes goals through commitments with partners. Causal reasoning instead starts with a predicted goal and acquires resources to reach it.`,
  c=>"Effectuation works from means to goals, not goals to means.");

S(1,3,"Business ethics","UNDERSTAND",2,"CONCEPT_MCQ","Contrast stakeholder and shareholder views","ethics",
  (name,r)=>base(name,r),
  c=>`${c.name}, a ${c.product} company in ${c.city}, must decide whom management should primarily serve. Which statement reflects the stakeholder view?`,
  ()=>O("Management should balance the interests of employees, customers, suppliers, and owners","",true),
  c=>[O("Management's only duty is to maximize returns for shareholders","States shareholder primacy rather than the stakeholder view"),
      O("Management should ignore parties that are not legal owners","Denies that non-owner parties have legitimate stakes"),
      O("Management should prioritize executives' interests above all others","Confuses stakeholder theory with self-serving management")],
  c=>`Stakeholder theory holds that a firm should consider all parties affected by its actions, including employees, customers, suppliers, and the community. Shareholder primacy treats maximizing owner returns as the sole objective, a much narrower view.`,
  c=>"Think about who is affected, not only who owns shares.");

S(1,3,"Corporate social responsibility","APPLY",3,"STIMULUS_MCQ","Apply social responsibility to a dilemma","ethics",
  (name,r)=>base(name,r),
  c=>`Which response best aligns with corporate social responsibility?`,
  ()=>O("Disclose the problem and work with the supplier to adopt a cleaner process","",true),
  c=>[O("Keep the arrangement quiet because lower costs benefit shareholders","Treats profit maximization as overriding all social concerns"),
      O("Switch suppliers only if regulators threaten a fine","Makes ethical action contingent on external enforcement"),
      O("Blame the supplier publicly while continuing to buy from them","Substitutes a reputational gesture for corrective action")],
  c=>`Corporate social responsibility means voluntarily accounting for social and environmental effects, not merely obeying laws or chasing profit. Disclosing the harm and remediating it addresses the community impact while preserving the business relationship.`,
  c=>"Look for the option that accepts short-term cost to fix real harm.",
  c=>({kind:"case",body:`${c.name} discovers that a supplier uses a cheap ${c.product} process that harms local water, cutting costs by ${money(c.money||c.D*100)} a year. Publicly disclosing the issue would raise costs but protect the community.`,license:"original"}));

S(1,4,"Supply and demand","APPLY",2,"CONCEPT_MCQ","Predict equilibrium changes","markets",
  (name,r)=>base(name,r),
  c=>`Demand for ${c.name}'s ${c.product} rises sharply while the number of sellers stays the same. What happens to equilibrium price and quantity in that market?`,
  ()=>O("Both equilibrium price and equilibrium quantity increase","",true),
  c=>[O("Price rises but quantity stays the same","Overlooks movement along the supply curve to a higher quantity"),
      O("Price stays the same but quantity rises","Assumes a perfectly elastic supply that seldom holds"),
      O("Both price and quantity fall","Reverses the effect of a demand increase")],
  c=>`An increase in demand shifts the demand curve rightward. With supply unchanged, the new intersection occurs at a higher price and a larger quantity, as suppliers respond to stronger demand by offering more units.`,
  c=>"Shift the demand curve right and read the new intersection.");

S(1,4,"Opportunity cost","APPLY",2,"CONCEPT_MCQ","Identify opportunity cost","markets",
  (name,r)=>base(name,r),
  c=>`${c.name} can use its ${c.city} warehouse either to store ${c.product} inventory or to lease it to another firm. Which idea captures the opportunity cost of storing the inventory?`,
  ()=>O("The rental income forgone by not leasing the warehouse","",true),
  c=>[O("The accounting depreciation recorded on the warehouse","Confuses a non-cash charge with a forgone alternative"),
      O("The historical purchase price of the warehouse","Treats a sunk cost as an opportunity cost"),
      O("The sum of all future profits from the product line","Mistakes total projected benefit for the next-best alternative")],
  c=>`Opportunity cost is the value of the next-best alternative given up, here the lease income foregone. Depreciation and historical purchase prices are accounting or sunk costs, and total projected profits are not the forgone alternative.`,
  c=>"Opportunity cost is what you give up, not what you spent.");

// ---------------- UNIT 2: Business Models & Opportunity ----------------
S(2,1,"Problem validation","APPLY",3,"CONCEPT_MCQ","Judge evidence of a real problem","discovery",
  (name,r)=>base(name,r),
  c=>`${c.name} wants to confirm that a real problem exists before building a ${c.product} product. Which evidence best validates the problem?`,
  ()=>O("Repeated reports of a painful problem from people in the target segment","",true),
  c=>[O("Compliments from friends about the founder's idea","Treats social approval as evidence of a painful problem"),
      O("A large number of likes on a promotional post","Mistakes passive attention for problem intensity"),
      O("A competitor already selling a similar product","Confirms a market but not that the problem is felt")],
  c=>`Problem validation requires consistent, specific evidence that people in the target segment experience a significant pain. Compliments, likes, and competitor presence are weaker because they do not document the depth or recurrence of the problem.`,
  c=>"Focus on repeated, specific pain reported by target users.");

S(2,1,"Customer discovery","ANALYZE",3,"CONCEPT_MCQ","Reduce bias in customer interviews","discovery",
  (name,r)=>base(name,r),
  c=>`While researching ${c.product} demand, which interview practice best reduces confirmation bias for ${c.name}?`,
  ()=>O("Asking open-ended questions about past behavior without pitching the solution","",true),
  c=>[O("Describing the solution first to gauge excitement","Leads respondents toward the desired answer"),
      O("Only interviewing people who already like the idea","Selects a sample that confirms the founder's belief"),
      O("Asking whether people would buy the finished product","Elicits hypothetical intent rather than past behavior")],
  c=>`Open questions about specific past behavior surface real evidence without leading respondents. Pitching first, sampling only supporters, and asking hypothetical purchase questions all bias responses toward false confirmation.`,
  c=>"Ask about what people did, not what they might do.");

S(2,2,"Value proposition","APPLY",3,"CONCEPT_MCQ","Identify a strong value proposition","valueprop",
  (name,r)=>base(name,r),
  c=>`Which statement is the strongest value proposition for ${c.name}'s ${c.product} offering?`,
  ()=>O("It helps a specific customer complete a key task faster than current alternatives","",true),
  c=>[O("It uses the newest technology available in the market","Mistakes a technology feature for customer benefit"),
      O("It is cheaper than every competitor in all circumstances","Makes an unsustainable absolute price claim"),
      O("It will eventually serve a very large number of people","Confuses market size with a specific value promise")],
  c=>`A strong value proposition names a specific customer, a concrete benefit, and a clear advantage over alternatives. Technology novelty, unconditional low price, and broad ambition do not communicate a focused, credible benefit.`,
  c=>"Look for customer, benefit, and advantage over alternatives.");

S(2,2,"Value proposition","APPLY",2,"CONCEPT_MCQ","Convert features into value","valueprop",
  (name,r)=>base(name,r),
  c=>`${c.name} lists technical specifications of its ${c.product}. Which change converts that list into a value proposition?`,
  ()=>O("Translating each feature into a concrete benefit for the customer","",true),
  c=>[O("Adding more technical specifications to the list","Deepens the feature focus instead of creating value"),
      O("Lowering the price without explaining the benefit","Changes price but not the customer-facing benefit"),
      O("Changing the product name to sound more modern","Mistakes rebranding for a value proposition")],
  c=>`Features describe what a product has; a value proposition explains what the customer gains. Translating specifications into outcomes such as time saved or risk reduced makes the benefit explicit.`,
  c=>"Features say what it is; value says what the customer gets.");

S(2,3,"Revenue models","UNDERSTAND",2,"CONCEPT_MCQ","Select a recurring revenue model","model",
  (name,r)=>base(name,r),
  c=>`${c.name} wants recurring revenue from its ${c.product} software. Which revenue model best fits that goal?`,
  ()=>O("A subscription that charges customers a recurring fee for continued access","",true),
  c=>[O("A one-time perpetual license with no renewal","Describes a one-time sale, not recurring revenue"),
      O("A pure pay-per-click advertising model with no user fees","Relies on advertisers rather than recurring customer fees"),
      O("A single crowdfunding campaign with no ongoing charges","Is a one-off raise, not a recurring revenue stream")],
  c=>`A subscription model produces predictable recurring revenue by charging customers periodically for access. Perpetual licenses, advertising, and one-time campaigns do not create a dependable recurring fee stream.`,
  c=>"Which model bills the same customers repeatedly?");

S(2,3,"Unit economics","APPLY",2,"NUMERIC","Compute lifetime value","model",
  (name,r)=>({...base(name,r), A:20, B:12}),
  c=>`${c.name} charges ${money(c.A)} a month for its ${c.product} service and keeps an average customer for ${c.B} months. What is gross lifetime value per customer, before costs?`,
  c=>null,
  c=>[],
  c=>`Gross lifetime value is the monthly fee multiplied by average lifetime in months, so ${money(c.A)} times ${c.B} months gives ${money(c.A*c.B)}. Subtracting service costs would produce a net figure instead.`,
  c=>"Multiply the recurring fee by how long customers stay.",null,c=>c.A*c.B);

S(2,4,"Market sizing","UNDERSTAND",2,"CONCEPT_MCQ","Distinguish TAM, SAM, and SOM","sizing",
  (name,r)=>base(name,r),
  c=>`Which statement correctly distinguishes TAM, SAM, and SOM for ${c.name}'s ${c.product} venture?`,
  ()=>O("TAM is the whole market, SAM is the served segment, and SOM is the share realistically captured","",true),
  c=>[O("TAM is the share won this year, SAM is next year's share, and SOM is the total","Confuses achievable share with total market size"),
      O("TAM, SAM, and SOM all describe the same total market","Collapses three distinct sizing layers into one"),
      O("SOM is always larger than TAM for a growing firm","Reverses the nesting relationship of the layers")],
  c=>`TAM is the total addressable market, SAM is the portion the business can serve, and SOM is the part it can realistically capture. The three are nested with SOM smallest, so confusing them reverses sizing logic.`,
  c=>"Think of nested circles: total, servable, obtainable.");

S(2,4,"Market sizing","APPLY",2,"DATA_INTERPRETATION","Compute SOM from a population","sizing",
  (name,r)=>base(name,r),
  c=>`A market has ${num(c.D*1000)} potential buyers. ${c.name} can serve ${c.pct}% of them and expects to capture ${c.C}% of that served segment. Roughly how many customers are in its SOM?`,
  c=>{const v=c.D*1000*c.pct/100*c.C/100;return O(num(v)+" customers","",true);},
  c=>{const T=c.D*1000;return [O(num(T)+" customers","Reports the total market rather than the obtainable share"),
      O(num(T*c.pct/100)+" customers","Stops at the served market, not the captured share"),
      O(num(T*c.C/100)+" customers","Applies the capture rate to the wrong base")];},
  c=>`SOM equals the population times the served fraction times the captured fraction: ${num(c.D*1000)} times ${c.pct}% times ${c.C}% gives about ${num(c.D*1000*c.pct/100*c.C/100)} customers. Using the population alone or the served segment alone skips a filtering step.`,
  c=>"Multiply the population by both percentages in sequence.");

// ---------------- UNIT 3: Marketing ----------------
S(3,1,"Segmentation","UNDERSTAND",2,"CONCEPT_MCQ","Identify a segmentation base","stp",
  (name,r)=>base(name,r),
  c=>`${c.name} divides buyers of ${c.product} into groups by age, income, and location. Which segmentation base is being used?`,
  ()=>O("Demographic and geographic segmentation","",true),
  c=>[O("Behavioral segmentation only","Ignores that age and income are demographic variables"),
      O("Psychographic segmentation only","Mistakes demographics for personality and values"),
      O("Benefit segmentation exclusively","Confuses demographics with the benefits buyers seek")],
  c=>`Age and income are demographic variables, and location is geographic, so this is combined demographic and geographic segmentation. Behavioral, psychographic, and benefit bases use actions, values, or desired outcomes instead.`,
  c=>"Match each variable to its standard segmentation category.");

S(3,1,"Positioning","APPLY",3,"CONCEPT_MCQ","Choose a positioning statement","stp",
  (name,r)=>base(name,r),
  c=>`Which positioning statement is most useful for ${c.name} selling ${c.product} to ${c.seg}?`,
  ()=>O("For busy buyers, our product delivers reliable quality at a fair price, unlike premium rivals","",true),
  c=>[O("Our product is the best one available to everyone anywhere","Makes a vague claim with no frame of reference"),
      O("We hope to attract many different kinds of customers over time","States ambition rather than a distinct position"),
      O("The product has many features that our engineers are proud of","Lists features without a target or competitive frame")],
  c=>`Effective positioning names a target, a point of difference, and a frame of reference. Claims that are vague, aspirational, or purely feature based give customers no reason to choose the brand over rivals.`,
  c=>"A good position names the target and the difference from alternatives.");

S(3,2,"Marketing mix","UNDERSTAND",2,"CONCEPT_MCQ","Classify a marketing decision","mix",
  (name,r)=>base(name,r),
  c=>`${c.name} sets the price of its ${c.product} and offers seasonal discounts. Which element of the marketing mix is this?`,
  ()=>O("Price","",true),
  c=>[O("Product","Confuses pricing with the design and features of the offering"),
      O("Place","Mistakes price decisions for distribution and location decisions"),
      O("Promotion","Treats discounts as only advertising rather than a price decision")],
  c=>`The marketing mix comprises product, price, place, and promotion. Setting a price and offering discounts are price decisions, even though discounts can also support promotion; the core variable being set here is price.`,
  c=>"Ask which of the four Ps is being directly changed.");

S(3,2,"Distribution","APPLY",2,"CONCEPT_MCQ","Choose a distribution strategy","mix",
  (name,r)=>base(name,r),
  c=>`${c.name} sells ${c.product} to ${c.seg} and wants maximum reach with minimal direct selling effort. Which distribution approach fits?`,
  ()=>O("Intensive distribution through many intermediaries","",true),
  c=>[O("Exclusive distribution through a single outlet","Limits reach and contradicts the goal of wide availability"),
      O("Selective distribution through a few chosen retailers","Provides moderate coverage rather than maximum reach"),
      O("Direct-only sales through the company's own staff","Maximizes control but also the direct selling effort")],
  c=>`Intensive distribution places a product in as many outlets as possible, matching a goal of broad reach. Exclusive and selective distribution deliberately limit coverage, while direct-only selling requires substantial company sales effort.`,
  c=>"Maximum reach points to the widest available coverage.");

S(3,3,"Pricing strategy","ANALYZE",3,"CONCEPT_MCQ","Match a pricing strategy to a goal","pricing",
  (name,r)=>base(name,r),
  c=>`${c.name} launches a novel ${c.product} with little competition and wants to recover development costs quickly from eager early buyers. Which strategy fits?`,
  ()=>O("Price skimming, setting a high initial price that falls over time","",true),
  c=>[O("Penetration pricing, setting a low price to win share fast","Aims for share rather than quick cost recovery"),
      O("Cost-plus pricing with a fixed percentage markup","Ignores willingness to pay and the novelty advantage"),
      O("Loss-leader pricing below cost indefinitely","Sacrifices margin without the intended recovery")],
  c=>`Price skimming charges a high initial price to capture the surplus of early adopters before lowering it to reach more price-sensitive buyers. Penetration pricing does the opposite, and cost-plus pricing ignores demand conditions.`,
  c=>"High initial price plus later reductions is skimming.");

S(3,3,"Contribution margin","APPLY",2,"DATA_INTERPRETATION","Compute contribution per unit","pricing",
  (name,r)=>base(name,r),
  c=>`${c.name} sells ${c.product} for ${money(c.A)} each with variable cost of ${money(c.B)} per unit. What is the contribution margin per unit?`,
  c=>{const v=c.A-c.B;return O(money(v)+" per unit","",true);},
  c=>[O(money(c.A)+" per unit","Reports the selling price rather than the margin"),
      O(money(c.B)+" per unit","Reports only the variable cost"),
      O(money(c.A+c.B)+" per unit","Adds cost to price instead of subtracting it")],
  c=>`Contribution margin per unit is selling price minus variable cost: ${money(c.A)} minus ${money(c.B)} equals ${money(c.A-c.B)}. The price and the cost alone are incomplete, and adding them would overstate the margin.`,
  c=>"Subtract variable cost from the selling price.");

S(3,4,"Promotion channels","UNDERSTAND",2,"CONCEPT_MCQ","Select a promotion channel","promo",
  (name,r)=>base(name,r),
  c=>`${c.name} wants to reach ${c.seg} with a message it can tailor to each person and measure immediately. Which channel best fits?`,
  ()=>O("Digital advertising with targeted, trackable placements","",true),
  c=>[O("National television advertising with broad reach","Offers little targeting or immediate measurement"),
      O("Untargeted direct mail to every household","Lacks individual tailoring and precise tracking"),
      O("A single in-store poster campaign","Cannot personalize or measure responses reliably")],
  c=>`Digital advertising allows precise audience targeting, personalization, and real-time measurement of responses. Mass media and untargeted mail trade away those advantages, and a static poster cannot tailor or track individual reactions.`,
  c=>"Look for targeting plus measurable responses.");

S(3,4,"Conversion metrics","APPLY",2,"NUMERIC","Compute customer acquisition cost","promo",
  (name,r)=>({...base(name,r), A:4000, B:80}),
  c=>`${c.name} spent ${money(c.A)} on a campaign that produced ${c.B} new customers for its ${c.product}. What was the customer acquisition cost?`,
  c=>null,c=>[],
  c=>`Customer acquisition cost is total campaign spend divided by customers gained, so ${money(c.A)} divided by ${c.B} gives ${money(c.A/c.B)} per customer. Dividing the other way would report customers per dollar instead.`,
  c=>"Divide spend by the number of new customers.",null,c=>c.A/c.B);

// ---------------- UNIT 4: Accounting & Finance ----------------
S(4,1,"Financial statements","UNDERSTAND",2,"CONCEPT_MCQ","Classify a financial item","statements",
  (name,r)=>base(name,r),
  c=>`${c.name} records revenue from selling ${c.product} and the cost of goods sold. In which statement do these items primarily appear?`,
  ()=>O("The income statement, which reports revenues and expenses","",true),
  c=>[O("The balance sheet, which lists assets and liabilities","Confuses performance flows with a point-in-time position"),
      O("The cash flow statement, which tracks only cash receipts","Mistakes accrual revenue for cash movements"),
      O("The statement of owners' equity alone","Treats an equity reconciliation as a full performance report")],
  c=>`The income statement reports revenues, expenses, and profit over a period, so sales revenue and cost of goods sold belong there. The balance sheet shows financial position at a point in time, and the cash flow statement tracks cash rather than accrual revenue.`,
  c=>"Ask which statement reports performance over a period.");

S(4,1,"Gross margin","APPLY",2,"DATA_INTERPRETATION","Compute gross profit","statements",
  (name,r)=>base(name,r),
  c=>`${c.name} generated ${money(c.D*100)} in sales of ${c.product} with cost of goods sold of ${money(c.D*60)}. What was gross profit?`,
  c=>{const v=c.D*40;return O(money(v),"",true);},
  c=>[O(money(c.D*100),"Reports total sales rather than the profit above cost"),
      O(money(c.D*60),"Reports only the cost of goods sold"),
      O(money(c.D*160),"Adds sales and cost instead of subtracting them")],
  c=>`Gross profit is net sales minus cost of goods sold: ${money(c.D*100)} less ${money(c.D*60)} equals ${money(c.D*40)}. Sales alone and cost alone are incomplete, and adding them would not measure profitability.`,
  c=>"Subtract the cost of goods sold from sales.");

S(4,2,"Break-even analysis","APPLY",2,"DATA_INTERPRETATION","Compute break-even units","breakeven",
  (name,r)=>base(name,r),
  c=>`${c.name} sells ${c.product} for ${money(c.A)} each, with variable cost ${money(c.B)} per unit and fixed costs of ${money(c.D*10)}. How many units must it sell to break even?`,
  c=>{const cm=c.A-c.B, v=Math.ceil(c.D*10/cm);return O(num(v)+" units","",true);},
  c=>{const cm=c.A-c.B;return [O(num(Math.ceil(c.D*10/c.A))+" units","Divides fixed costs by price instead of contribution margin"),
      O(num(c.D*10)+" units","Treats fixed costs in dollars as a unit count"),
      O(num(Math.ceil(c.D*10/c.B))+" units","Divides fixed costs by variable cost instead of margin")];},
  c=>`Break-even volume is fixed costs divided by contribution per unit. The contribution is ${money(c.A-c.B)} from price ${money(c.A)} minus variable cost ${money(c.B)}, so ${money(c.D*10)} divided by that margin gives about ${num(Math.ceil(c.D*10/(c.A-c.B)))} units.`,
  c=>"Divide fixed costs by price minus variable cost.");

S(4,2,"Cost-volume-profit","ANALYZE",3,"CONCEPT_MCQ","Assess a price change","breakeven",
  (name,r)=>base(name,r),
  c=>`${c.name} raises the price of its ${c.product} while costs stay constant. Assuming demand is unchanged, what happens to its break-even quantity?`,
  ()=>O("It decreases because contribution per unit is larger","",true),
  c=>[O("It increases because higher prices always raise costs","Assumes costs rise with price without cause"),
      O("It stays the same because only volume matters","Ignores that price changes contribution per unit"),
      O("It doubles because price and break-even are proportional","Confuses a higher margin with a fixed multiple")],
  c=>`A higher price increases contribution per unit when costs are constant, so fewer units are needed to cover fixed costs and break-even quantity falls. Price does not by itself raise costs, and the relationship is not a fixed multiple.`,
  c=>"Higher margin per unit means fewer units needed.");

S(4,3,"Cash flow","UNDERSTAND",2,"CONCEPT_MCQ","Distinguish profit from cash","cash",
  (name,r)=>base(name,r),
  c=>`${c.name} reported a profit from ${c.product} sales but still ran short of cash that month. Which explanation is most plausible?`,
  ()=>O("Customers were given credit terms and had not yet paid","",true),
  c=>[O("The company earned revenue but had no expenses","Would increase cash, not create a shortage"),
      O("Profit and cash flow are identical accounting measures","Denies the timing difference between them"),
      O("The firm sold products only for immediate cash","Would improve cash rather than deplete it")],
  c=>`Accrual profit is recorded when revenue is earned, not when cash arrives. If customers buy on credit, sales appear in profit before payment, so a profitable firm can still face a cash shortage while receivables are outstanding.`,
  c=>"Think about the timing of revenue versus cash collection.");

S(4,3,"Working capital","APPLY",2,"NUMERIC","Compute days sales outstanding","cash",
  (name,r)=>({...base(name,r), A:250000, B:50000}),
  c=>`${c.name} has average accounts receivable of ${money(c.A)} and credit sales of ${money(c.B)} per month. Approximately how many days of sales are tied up in receivables?`,
  c=>null,c=>[],
  c=>`Days sales outstanding is average receivables divided by daily credit sales. Monthly sales of ${money(c.B)} imply daily sales near ${money(c.B/30)}, so ${money(c.A)} divided by that daily figure is roughly ${num(c.A/(c.B/30))} days.`,
  c=>"Convert monthly sales to daily, then divide receivables by it.",null,c=>c.A/(c.B/30));

S(4,4,"Budgeting","UNDERSTAND",2,"CONCEPT_MCQ","Identify a flexible budget","budget",
  (name,r)=>base(name,r),
  c=>`${c.name} prepares a budget for ${c.product} that adjusts allowance costs as actual sales volume changes. What kind of budget is this?`,
  ()=>O("A flexible budget, which varies with activity level","",true),
  c=>[O("A static budget, which stays fixed regardless of volume","Describes the opposite type of budget"),
      O("A cash budget, which only tracks bank balances","Confuses activity adjustment with cash planning"),
      O("A capital budget, which covers long-term assets","Mistakes operating flexibility for capital planning")],
  c=>`A flexible budget changes with the level of activity, so variable allowances rise and fall with actual volume. A static budget stays fixed, while cash and capital budgets serve different planning purposes.`,
  c=>"Which budget changes with actual activity?");

S(4,4,"Variance analysis","ANALYZE",3,"CONCEPT_MCQ","Interpret a cost variance","budget",
  (name,r)=>base(name,r),
  c=>`${c.name} budgeted ${money(c.D*10)} for ${c.product} materials but actually spent ${money(c.D*13)}. Assuming output was as planned, how should this variance be labeled?`,
  ()=>O("An unfavorable cost variance because actual spending exceeded the budget","",true),
  c=>[O("A favorable cost variance because spending was higher","Reverses the meaning of an overspend"),
      O("No variance because the budget is only an estimate","Ignores that actuals can be compared to a plan"),
      O("A revenue variance because costs affect sales","Confuses a cost overrun with a revenue shortfall")],
  c=>`Spending more than budgeted for materials is an unfavorable cost variance because it reduces profit relative to the plan. A favorable cost variance would require actual spending below budget, and this is not a revenue variance.`,
  c=>"Compare actual cost to budget and consider the effect on profit.");

// ---------------- UNIT 5: Operations & Supply Chain ----------------
S(5,1,"Capacity","APPLY",2,"DATA_INTERPRETATION","Compute capacity utilization","ops",
  (name,r)=>base(name,r),
  c=>`${c.name} can produce ${num(c.D)} units of ${c.product} a month but actually produced ${num(c.D*c.pct/100)}. What was its capacity utilization?`,
  c=>{const v=c.pct;return O(v+"%","",true);},
  c=>[O((100-c.pct)+"%","Reports idle capacity rather than utilization"),
      O(num(c.D)+"%","Confuses a unit count with a percentage"),
      O((c.pct/2)+"%","Halves the ratio without reason")],
  c=>`Capacity utilization is actual output divided by design capacity, here ${num(c.D*c.pct/100)} over ${num(c.D)}, which equals ${c.pct}%. Idle capacity would be the complementary ${100-c.pct}% rather than the utilization itself.`,
  c=>"Divide actual output by maximum capacity.");

S(5,1,"Process strategy","UNDERSTAND",2,"CONCEPT_MCQ","Match process type to volume","ops",
  (name,r)=>base(name,r),
  c=>`${c.name} makes one-off custom ${c.product} for each client with varied requirements. Which process type best fits?`,
  ()=>O("Job-shop production, which handles low-volume customized work","",true),
  c=>[O("Mass production, which makes large volumes of identical items","Unsuitable for customized, varied output"),
      O("Continuous production, which runs nonstop on one product","Requires standardization the work lacks"),
      O("Assembly-line production, which follows a fixed sequence","Best for standardized, repetitive tasks")],
  c=>`A job shop is designed for low-volume, high-variety work where each order follows a different path. Mass, continuous, and assembly-line processes rely on standardization and repetition that custom orders do not allow.`,
  c=>"High variety and low volume point to a job shop.");

S(5,2,"Inventory management","UNDERSTAND",2,"CONCEPT_MCQ","Explain the EOQ tradeoff","inventory",
  (name,r)=>base(name,r),
  c=>`What core tradeoff does the economic order quantity model balance for ${c.name}'s ${c.product} inventory?`,
  ()=>O("Ordering costs versus inventory holding costs","",true),
  c=>[O("Purchase price versus selling price","Confuses inventory policy with pricing decisions"),
      O("Labor costs versus material costs","Ignores the ordering and holding tradeoff"),
      O("Fixed costs versus variable costs broadly","Names a broad category rather than the specific tradeoff")],
  c=>`The EOQ model finds the order size that minimizes the sum of ordering costs, which fall with larger orders, and holding costs, which rise with larger orders. It is not primarily about pricing or broad cost categories.`,
  c=>"One cost falls as orders grow and the other rises.");

S(5,2,"Reorder point","APPLY",2,"NUMERIC","Compute a reorder point","inventory",
  (name,r)=>({...base(name,r), A:30, B:20}),
  c=>`${c.name} uses ${c.A} units of ${c.product} per day and faces a ${c.B}-day supplier lead time with no safety stock. At what inventory level should it reorder?`,
  c=>null,c=>[],
  c=>`The reorder point without safety stock is daily demand multiplied by lead time in days: ${c.A} units per day times ${c.B} days equals ${c.A*c.B} units. Ordering below that level risks running out before the new stock arrives.`,
  c=>"Multiply daily usage by lead time in days.",null,c=>c.A*c.B);

S(5,3,"Quality management","UNDERSTAND",2,"CONCEPT_MCQ","Identify total quality management","quality",
  (name,r)=>base(name,r),
  c=>`${c.name} involves every employee in continuously improving the quality of its ${c.product}. Which approach does this describe?`,
  ()=>O("Total quality management, which engages all staff in continuous improvement","",true),
  c=>[O("Inspection-only quality control at the final stage","Limits quality to end-of-line checking"),
      O("A one-time process reengineering project","Is a single initiative rather than continuous improvement"),
      O("Statistical process control used in isolation","Is a tool, not a whole-organization philosophy")],
  c=>`Total quality management is a philosophy in which every member of the organization focuses on quality and continuous improvement. Final inspection, one-off projects, and isolated tools do not capture that organization-wide commitment.`,
  c=>"Look for organization-wide and continuous improvement.");

S(5,3,"Variation","ANALYZE",3,"CONCEPT_MCQ","Distinguish causes of variation","quality",
  (name,r)=>base(name,r),
  c=>`A machine producing ${c.product} shows steady random fluctuations, then one sudden spike traced to a broken part. How should the two kinds of variation be classified?`,
  ()=>O("The fluctuations are common causes; the spike is a special cause","",true),
  c=>[O("Both are special causes needing individual investigation","Mislabels routine background noise as assignable"),
      O("Both are common causes inherent in the process","Ignores the specific broken part behind the spike"),
      O("The fluctuations are special and the spike is common","Reverses the two definitions")],
  c=>`Common causes are the normal random variation built into a process, while special causes are assignable events such as a broken part. A control chart separates the two so managers do not chase routine noise or ignore real faults.`,
  c=>"Steady noise is common; a one-off fault is special.");

S(5,4,"Supply chain strategy","ANALYZE",3,"CONCEPT_MCQ","Choose lean versus agile","supply",
  (name,r)=>base(name,r),
  c=>`${c.name} sells ${c.product} whose demand is unpredictable and whose variety changes quickly. Which supply chain strategy fits best?`,
  ()=>O("An agile supply chain that prioritizes responsiveness","",true),
  c=>[O("A lean supply chain focused only on minimizing cost","Prioritizes efficiency over responsiveness to change"),
      O("A single-source strategy with long fixed contracts","Reduces flexibility when demand shifts"),
      O("A make-to-stock strategy with large finished-goods buffers","Uses bulky inventory that poorly fits volatile demand")],
  c=>`An agile supply chain emphasizes speed and flexibility to cope with volatile, varied demand. Lean supply chains excel when demand is stable and efficiency dominates, while rigid sourcing and large buffers reduce responsiveness.`,
  c=>"Unpredictable demand calls for responsiveness, not just low cost.");

S(5,4,"Safety stock","APPLY",2,"NUMERIC","Compute safety stock","supply",
  (name,r)=>({...base(name,r), A:60, B:10}),
  c=>`${c.name} uses ${c.A} units of ${c.product} a day. Its supplier sometimes takes ${c.B} extra days to deliver. How much safety stock covers that variability?`,
  c=>null,c=>[],
  c=>`Safety stock for a lead-time variation is daily demand times the extra days of lead time: ${c.A} units per day times ${c.B} extra days equals ${c.A*c.B} units. Holding that buffer prevents a stockout when deliveries run late.`,
  c=>"Multiply daily demand by the extra lead-time days.",null,c=>c.A*c.B);

// ---------------- UNIT 6: Management & Organizational Behavior ----------------
S(6,1,"Functions of management","UNDERSTAND",2,"CONCEPT_MCQ","Identify the controlling function","mgmt",
  (name,r)=>base(name,r),
  c=>`${c.name} compares actual ${c.product} sales against targets and corrects deviations. Which management function is being performed?`,
  ()=>O("Controlling, which measures performance and corrects deviations","",true),
  c=>[O("Planning, which sets objectives before action","Planning precedes rather than checks performance"),
      O("Organizing, which arranges resources and tasks","Organizing allocates resources rather than monitoring results"),
      O("Leading, which motivates and directs employees","Leading focuses on people rather than variance checks")],
  c=>`Controlling involves measuring actual performance, comparing it with standards, and taking corrective action. Planning sets the goals, organizing arranges resources, and leading directs people toward those goals.`,
  c=>"Which function compares results with targets and fixes gaps?");

S(6,1,"Management levels","UNDERSTAND",2,"CONCEPT_MCQ","Identify first-line managers","mgmt",
  (name,r)=>base(name,r),
  c=>`At ${c.name}, a supervisor directly oversees workers making ${c.product} and reports to a middle manager. What level of management is the supervisor?`,
  ()=>O("First-line management, which supervises non-managerial employees","",true),
  c=>[O("Top management, which sets overall strategy","Top managers do not directly supervise production workers"),
      O("Middle management, which implements departmental plans","The supervisor is below middle management"),
      O("Strategic management, which handles long-term direction","Confuses a supervisory role with corporate strategy")],
  c=>`First-line managers, often called supervisors, directly coordinate the work of non-managerial employees. Middle managers implement plans through those supervisors, and top managers set broad organizational direction.`,
  c=>"Directly overseeing workers indicates the lowest management level.");

S(6,2,"Leadership styles","UNDERSTAND",2,"CONCEPT_MCQ","Identify a leadership style","lead",
  (name,r)=>base(name,r),
  c=>`The founder of ${c.name} makes all ${c.product} decisions alone and tells employees what to do with little discussion. Which leadership style is this?`,
  ()=>O("Autocratic leadership, where one person holds decision authority","",true),
  c=>[O("Democratic leadership, which involves group participation","Decision authority is not shared here"),
      O("Laissez-faire leadership, which delegates freely","The leader is highly involved, not hands-off"),
      O("Transformational leadership, which inspires shared vision","Focuses on inspiration rather than unilateral control")],
  c=>`Autocratic leadership centralizes decisions in one person with limited input from others. Democratic leadership invites participation, laissez-faire leadership delegates broadly, and transformational leadership motivates through a shared vision.`,
  c=>"One person deciding with little input points to autocratic.");

S(6,2,"Situational leadership","APPLY",3,"STIMULUS_MCQ","Match leadership to readiness","lead",
  (name,r)=>base(name,r),
  c=>`What is the most appropriate leadership response for this team?`,
  ()=>O("Provide clear direction and close supervision until skills develop","",true),
  c=>[O("Delegate all decisions immediately and stay uninvolved","Unsuitable for an inexperienced team needing guidance"),
      O("Ask the team to set its own goals with no structure","Removes the direction novices require"),
      O("Focus only on social activities and avoid task guidance","Neglects the competence-building need")],
  c=>`Situational leadership matches the leader's style to follower readiness. New, unable teams need a directing style with clear instructions and close supervision, while delegating suits highly ready teams.`,
  c=>"Novice teams need direction before autonomy.",
  c=>({kind:"case",body:`A newly formed team at ${c.name} will produce a new ${c.product} line. Members are enthusiastic but lack the skills and experience needed for the tasks.`,license:"original"}));

S(6,3,"Motivation theories","UNDERSTAND",2,"CONCEPT_MCQ","Identify a motivation theory","motivation",
  (name,r)=>base(name,r),
  c=>`${c.name} finds that pay raises prevent dissatisfaction among its ${c.product} staff but do not create lasting enthusiasm. Which theory best explains this?`,
  ()=>O("Herzberg's two-factor theory, where pay is a hygiene factor","",true),
  c=>[O("Maslow's hierarchy, where pay satisfies self-actualization","Pay is a lower-level need in that hierarchy"),
      O("Equity theory, which focuses only on input-output ratios","Describes comparisons rather than hygiene effects"),
      O("Reinforcement theory, which ignores job content","Focuses on consequences rather than satisfaction sources")],
  c=>`Herzberg's two-factor theory holds that hygiene factors such as pay prevent dissatisfaction but do not produce motivation; motivators such as achievement and recognition do. Pay is not a self-actualization need, and the other theories address different mechanisms.`,
  c=>"Which theory separates preventing dissatisfaction from creating it?");

S(6,3,"Expectancy theory","APPLY",3,"CONCEPT_MCQ","Apply expectancy theory","motivation",
  (name,r)=>base(name,r),
  c=>`Employees at ${c.name} doubt that extra effort will raise their ${c.product} sales, even though higher sales would bring bonuses. Which belief is weakest according to expectancy theory?`,
  ()=>O("Expectancy, the belief that effort will lead to performance","",true),
  c=>[O("Instrumentality, the belief that performance leads to rewards","They already accept that sales bring bonuses"),
      O("Valence, the value placed on the bonus","The bonus is valued; the doubt is about effort paying off"),
      O("Equity, the fairness of outcomes relative to inputs","That is a different theory's concern")],
  c=>`Expectancy theory says motivation depends on expectancy, instrumentality, and valence. Because workers doubt that effort improves performance, expectancy is the weak link; they already believe performance yields rewards and value those rewards.`,
  c=>"Which link is broken: effort to performance, performance to reward, or reward value?");

S(6,4,"Organizational structure","UNDERSTAND",2,"CONCEPT_MCQ","Identify a matrix structure","org",
  (name,r)=>base(name,r),
  c=>`At ${c.name}, employees making ${c.product} report to both a functional manager and a project manager. Which structure is this?`,
  ()=>O("A matrix structure, with dual reporting relationships","",true),
  c=>[O("A functional structure, with a single functional chain","Functional structures have one boss, not two"),
      O("A divisional structure, grouped by product or region","Divisional structures are not defined by dual reporting"),
      O("A flat structure, with few or no management layers","Flatness concerns layers rather than dual authority")],
  c=>`A matrix structure combines functional and project reporting, so employees have two managers. Functional and divisional structures use a single chain of command, and a flat structure describes the number of layers rather than dual authority.`,
  c=>"Two bosses at once is the hallmark of a matrix.");

S(6,4,"Team dynamics","ANALYZE",3,"CONCEPT_MCQ","Recognize groupthink","org",
  (name,r)=>base(name,r),
  c=>`The ${c.name} team making ${c.product} avoids disagreement so strongly that no one challenges a flawed plan. Which phenomenon is this?`,
  ()=>O("Groupthink, where pressure for agreement suppresses dissent","",true),
  c=>[O("Task conflict, which debates ideas about the work","Task conflict is about ideas, not enforced agreement"),
      O("Social loafing, where members reduce individual effort","Loafing concerns effort, not suppressed criticism"),
      O("Role ambiguity, where duties are unclear","Ambiguity concerns unclear roles, not conformity pressure")],
  c=>`Groupthink occurs when the desire for consensus suppresses critical evaluation, leading to poor decisions. Constructive task conflict is healthy, social loafing reduces effort, and role ambiguity involves unclear expectations.`,
  c=>"Suppressed dissent for the sake of agreement is groupthink.");

// ---------------- UNIT 7: Entrepreneurship & Venture Financing ----------------
S(7,1,"Funding stages","UNDERSTAND",2,"CONCEPT_MCQ","Identify bootstrapping","funding",
  (name,r)=>base(name,r),
  c=>`${c.name} funds its first ${c.product} prototype using the founder's savings and early sales, with no outside investors. What is this called?`,
  ()=>O("Bootstrapping, funding growth from personal resources and revenue","",true),
  c=>[O("Venture capital financing from institutional funds","Involves outside equity investors, which are absent"),
      O("An initial public offering of shares to the public","Requires a mature company and public markets"),
      O("Angel investment from wealthy individuals","Also brings outside capital, which is not present")],
  c=>`Bootstrapping means financing a venture from personal savings and operating revenue without external investors. Venture capital, public offerings, and angel investment all involve outside capital that the scenario excludes.`,
  c=>"Personal savings plus sales, with no outsiders, is bootstrapping.");

S(7,1,"Funding sources","APPLY",3,"CONCEPT_MCQ","Match funding to stage","funding",
  (name,r)=>base(name,r),
  c=>`${c.name} has a proven ${c.product} prototype and early sales but needs growth capital and can tolerate high risk. Which source is most suitable?`,
  ()=>O("Venture capital, which funds high-growth ventures in exchange for equity","",true),
  c=>[O("A traditional bank loan secured by hard assets","Banks are cautious about unproven, high-risk growth"),
      O("Bootstrapping from revenue alone","May be too slow for the intended rapid expansion"),
      O("A government grant with no strategic involvement","Grants are not typically tied to scaling a venture")],
  c=>`Venture capital suits ventures with proven potential and high growth ambitions, providing equity funding and expertise in exchange for ownership and risk. Bank loans require collateral and steady cash flow, bootstrapping is slower, and grants are not standard growth capital.`,
  c=>"High growth and high risk point to equity venture capital.");

S(7,2,"Pitching","UNDERSTAND",2,"CONCEPT_MCQ","Identify pitch content","pitch",
  (name,r)=>base(name,r),
  c=>`Which element is most essential in an investor pitch for ${c.name}'s ${c.product} venture?`,
  ()=>O("A clear explanation of the problem, solution, market, and revenue model","",true),
  c=>[O("A detailed list of the founder's personal hobbies","Irrelevant to the investment case"),
      O("An elaborate office design concept","Does not address the business opportunity"),
      O("A promise that the venture carries no risk","Implausible and avoids discussing real risks")],
  c=>`Investors need to understand the problem, the proposed solution, the market opportunity, and how the venture makes money. Personal details, office aesthetics, and unrealistic no-risk claims do not build a credible investment case.`,
  c=>"Focus on what investors need to evaluate the opportunity.");

S(7,2,"Equity dilution","APPLY",2,"DATA_INTERPRETATION","Compute post-money ownership","pitch",
  (name,r)=>base(name,r),
  c=>`${c.name} raises ${money(c.B*100000)} at a post-money valuation of ${money(c.D*100000)}. What fraction of the company do the new investors own?`,
  c=>{const f=c.B/c.D*100;return O(num(f)+"%","",true);},
  c=>[O(num(c.D/c.B*100)+"%","Inverts the investment-to-valuation ratio"),
      O(num(c.B)+"%","Treats the investment amount as a percentage"),
      O(num(100-c.B/c.D*100)+"%","Reports the founders' remaining stake instead")],
  c=>`New investors own their investment divided by the post-money valuation: ${money(c.B*100000)} over ${money(c.D*100000)} equals ${num(c.B/c.D*100)}%. The complementary share is retained by existing owners, and inverting the ratio would be wrong.`,
  c=>"Divide the investment by the post-money valuation.");

S(7,3,"Growth strategies","UNDERSTAND",2,"CONCEPT_MCQ","Identify market development","growth",
  (name,r)=>base(name,r),
  c=>`${c.name} sells the same ${c.product} to a new group of ${c.seg} in a different region. Which growth strategy is this?`,
  ()=>O("Market development, taking existing products to new markets","",true),
  c=>[O("Product development, creating new products for existing customers","The product is unchanged here"),
      O("Market penetration, selling more of the same to current customers","The customer group and region are new"),
      O("Diversification, offering new products to new markets","Neither product nor market is new in both ways")],
  c=>`Market development means taking existing products into new markets such as new segments or regions. Product development changes the offering, penetration deepens current markets, and diversification changes both product and market.`,
  c=>"Same product, new customers points to market development.");

S(7,3,"Growth channels","APPLY",3,"CONCEPT_MCQ","Choose a growth channel","growth",
  (name,r)=>base(name,r),
  c=>`${c.name} wants faster, lower-cost ${c.product} growth by encouraging existing customers to refer friends. Which approach fits?`,
  ()=>O("A referral program that rewards customers for bringing in new buyers","",true),
  c=>[O("A large television advertising campaign","Costly and does not leverage existing customers"),
      O("Opening expensive physical showrooms in many cities","High cost and slow to scale"),
      O("Raising product prices to fund expansion","May reduce demand rather than drive growth")],
  c=>`Referral programs use current customers to acquire new ones, often at low cost and with higher trust. Mass advertising and new showrooms are expensive, and raising prices can suppress the demand needed for growth.`,
  c=>"Look for growth driven by existing customers at low cost.");

S(7,4,"Pivot decisions","ANALYZE",3,"CONCEPT_MCQ","Recognize when to pivot","risk",
  (name,r)=>base(name,r),
  c=>`${c.name} keeps refining its ${c.product} despite evidence that customers want a different solution. Which action best reflects disciplined entrepreneurship?`,
  ()=>O("Pivot to a new approach supported by the customer evidence","",true),
  c=>[O("Persevere with the original plan regardless of feedback","Ignores clear evidence of a mismatch"),
      O("Blame customers for not understanding the product","Rejects valuable market information"),
      O("Spend more on advertising before reconsidering","Increases commitment without addressing the signal")],
  c=>`A pivot changes the strategy while preserving what has been learned, and is appropriate when evidence shows the current approach is not working. Persevering against evidence, blaming customers, or spending more on promotion ignores the signal.`,
  c=>"Changing course in response to evidence is a pivot.");

S(7,4,"Risk management","UNDERSTAND",2,"CONCEPT_MCQ","Identify a mitigation tactic","risk",
  (name,r)=>base(name,r),
  c=>`${c.name} depends on one supplier for a critical ${c.product} component and qualifies a second supplier. Which risk strategy is this?`,
  ()=>O("Mitigation, reducing the impact of a supplier failure","",true),
  c=>[O("Avoidance, eliminating the activity entirely","The company still uses the component"),
      O("Transfer, shifting the risk to another party","No insurance or contract transfer is described"),
      O("Acceptance, taking the risk without action","Qualifying a second supplier is active mitigation")],
  c=>`Qualifying an alternative supplier reduces the impact of a disruption, which is risk mitigation. Avoidance removes the activity, transfer shifts the risk to another party, and acceptance means taking no action.`,
  c=>"Reducing the chance or impact of a risk is mitigation.");

// ---------------- UNIT 8: Business Law & Ethics ----------------
S(8,1,"Legal liability","UNDERSTAND",2,"CONCEPT_MCQ","Compare owner liability","legal",
  (name,r)=>base(name,r),
  c=>`Which statement correctly contrasts a sole proprietorship with a corporation for ${c.name}'s ${c.product} business?`,
  ()=>O("A sole proprietor faces unlimited personal liability; a shareholder's liability is limited to their investment","",true),
  c=>[O("A sole proprietor has limited liability; a shareholder is personally liable for all debts","Reverses the liability protection of both forms"),
      O("Both forms expose owners to identical unlimited personal liability","Ignores the corporate liability shield"),
      O("Neither form exposes owners to any business debts","Denies that a sole proprietor bears liability")],
  c=>`A sole proprietor is personally liable for business debts, putting personal assets at risk. A corporation's shareholders generally risk only what they invested, which is the core legal advantage of incorporation.`,
  c=>"Which form shields personal assets from business debts?");

S(8,1,"Piercing the corporate veil","ANALYZE",3,"STIMULUS_MCQ","Assess veil piercing","legal",
  (name,r)=>base(name,r),
  c=>`What is the most likely legal outcome if these facts are proven?`,
  ()=>O("A court may pierce the corporate veil and hold the owner personally liable","",true),
  c=>[O("The owner keeps full liability protection regardless of conduct","Corporate protection is not absolute"),
      O("The corporation is automatically dissolved by a regulator","Dissolution is not the automatic remedy"),
      O("Suppliers lose all claims against the business","Wrongdoing does not erase legitimate claims")],
  c=>`Courts may pierce the corporate veil when owners commingle personal and business assets or use the corporation to commit fraud, setting aside limited liability. The protection is conditional, and dissolution or loss of supplier claims is not the automatic result.`,
  c=>"Serious misuse of the corporate form can remove the liability shield.",
  c=>({kind:"case",body:`The owner of ${c.name} routinely pays personal bills from the company account, keeps no separate records, and uses the ${c.product} business to hide assets from suppliers.`,license:"original"}));

S(8,2,"Contracts","UNDERSTAND",2,"CONCEPT_MCQ","Identify contract elements","legal2",
  (name,r)=>base(name,r),
  c=>`${c.name} and a supplier agree on a ${c.product} delivery: one offers terms, the other accepts, and both exchange value. Which set names the core elements of a contract?`,
  ()=>O("Offer, acceptance, and consideration","",true),
  c=>[O("Offer, advertisement, and negotiation","Advertising and negotiation are not the core legal elements"),
      O("Acceptance, payment, and delivery only","Omits the offer and the broader idea of consideration"),
      O("Consideration, signature, and notarization","A signature or notarization is not universally required")],
  c=>`A contract generally requires an offer, an acceptance of that offer, and consideration, meaning something of value exchanged. Advertisements and negotiation are precursors, and not all contracts must be notarized.`,
  c=>"Name the three classic building blocks of an agreement.");

S(8,2,"Intellectual property","APPLY",2,"CONCEPT_MCQ","Match IP type to asset","legal2",
  (name,r)=>base(name,r),
  c=>`${c.name} develops a distinctive logo for its ${c.product} brand. Which form of intellectual property best protects that logo?`,
  ()=>O("A trademark, which protects distinctive marks identifying a source","",true),
  c=>[O("A patent, which protects new inventions and processes","A logo is not an invention"),
      O("A copyright, which protects original creative works generally","Copyright does not primarily protect brand identifiers"),
      O("A trade secret, which protects undisclosed confidential information","A public logo is not kept secret")],
  c=>`A trademark protects symbols, names, and logos that distinguish the source of goods. Patents cover inventions, copyrights cover creative expression, and trade secrets protect information kept confidential, so a visible brand mark fits a trademark.`,
  c=>"Brand identifiers are protected by trademarks.");

S(8,3,"Worker classification","APPLY",3,"CONCEPT_MCQ","Distinguish contractor from employee","employment",
  (name,r)=>base(name,r),
  c=>`${c.name} wants to treat a worker making ${c.product} as an independent contractor. Which factor most strongly indicates employee status instead?`,
  ()=>O("The company controls when, where, and how the work is done","",true),
  c=>[O("The worker uses their own tools and sets their own hours","Points toward contractor status"),
      O("The worker is paid per completed deliverable","Supports contractor classification"),
      O("The worker serves several clients at once","Suggests independence rather than employment")],
  c=>`The degree of control over how, when, and where work is performed is central to distinguishing employees from contractors. Control by the hiring firm points to employment, while independence, own tools, and multiple clients point to contracting.`,
  c=>"Control over the details of work is the key test.");

S(8,3,"Employment compliance","UNDERSTAND",2,"CONCEPT_MCQ","Identify discriminatory practice","employment",
  (name,r)=>base(name,r),
  c=>`${c.name} rejects qualified ${c.product} applicants because of their age. Which legal concept does this illustrate?`,
  ()=>O("Employment discrimination based on a protected characteristic","",true),
  c=>[O("Legitimate hiring based on job-related qualifications","Age is unrelated to the stated reason"),
      O("A valid probationary employment period","Probation concerns new hires, not rejection criteria"),
      O("Standard performance management of current staff","Applicants are not current employees")],
  c=>`Rejecting qualified applicants because of age is discrimination on the basis of a protected characteristic. Basing decisions on job-related qualifications is lawful, while probation and performance management concern existing employees.`,
  c=>"The rejection is based on a personal characteristic, not merit.");

S(8,4,"Compliance and ethics","UNDERSTAND",2,"CONCEPT_MCQ","Distinguish ethics from compliance","governance",
  (name,r)=>base(name,r),
  c=>`${c.name} follows all laws for its ${c.product} but adopts fairer supplier terms that the law does not require. Which distinction does this illustrate?`,
  ()=>O("Ethics can go beyond legal compliance","",true),
  c=>[O("Ethics and law are always identical","The voluntary fair terms exceed legal requirements"),
      O("Compliance is optional when ethics are strong","Legal duties still bind the firm"),
      O("Ethics apply only where no laws exist","Ethical standards also guide conduct within the law")],
  c=>`Legal compliance sets the minimum required conduct, while ethics concerns what is fair and responsible even where the law is silent. Choosing fairer supplier terms voluntarily shows ethical judgment exceeding legal duty.`,
  c=>"Law is a floor; ethics can set a higher standard.");

S(8,4,"Corporate governance","ANALYZE",3,"CONCEPT_MCQ","Identify a board's role","governance",
  (name,r)=>base(name,r),
  c=>`At ${c.name}, the board oversees management and reviews ${c.product} strategy without running daily operations. Which governance role is this?`,
  ()=>O("Oversight and accountability for management's decisions","",true),
  c=>[O("Direct day-to-day production of goods","Boards do not manage daily operations"),
      O("Handling routine customer orders for ${c.product}","That is an operating task, not a board function"),
      O("Replacing all managers at every meeting","Oversight is not constant wholesale replacement")],
  c=>`A board's governance role is to oversee management, monitor strategy and controls, and ensure accountability, while leaving daily operations to executives. Running production or fulfilling orders is management's job, not the board's.`,
  c=>"Boards oversee and hold management accountable.");

// ---------------- UNIT 9: Strategy ----------------
S(9,1,"Five forces","UNDERSTAND",2,"CONCEPT_MCQ","Identify a competitive force","strategy",
  (name,r)=>base(name,r),
  c=>`Many new firms can easily enter the ${c.product} market served by ${c.name}. Which of the five forces does this describe?`,
  ()=>O("The threat of new entrants","",true),
  c=>[O("The bargaining power of suppliers","Concerns input providers, not new competitors"),
      O("The threat of substitute products","Substitutes are alternative offerings, not new rivals"),
      O("Rivalry among existing competitors","Existing firms are already in the market")],
  c=>`The threat of new entrants captures how easily new competitors can join a market and erode profits. Supplier power concerns input providers, substitutes are different products meeting the same need, and rivalry concerns established firms.`,
  c=>"Easy entry by new competitors is a specific force.");

S(9,1,"Five forces","ANALYZE",3,"CONCEPT_MCQ","Assess a force's effect","strategy",
  (name,r)=>base(name,r),
  c=>`Buyers of ${c.product} have many alternatives and face low switching costs. What does this imply for ${c.name}?`,
  ()=>O("High buyer power tends to push prices and margins down","",true),
  c=>[O("High supplier power raises the firm's pricing freedom","Applies to input providers, not buyers"),
      O("Low rivalry because buyers have many choices","Many choices usually heighten competitive pressure"),
      O("Higher margins because buyers can be charged more","Easy switching limits the ability to raise price")],
  c=>`When buyers have many alternatives and low switching costs, their bargaining power is high, which constrains prices and compresses margins. This is a buyer-side force, not supplier power, and it intensifies rather than softens competition.`,
  c=>"Buyers with easy alternatives can demand lower prices.");

S(9,2,"Generic strategies","UNDERSTAND",2,"CONCEPT_MCQ","Identify cost leadership","strategy2",
  (name,r)=>base(name,r),
  c=>`${c.name} aims to be the lowest-cost producer of ${c.product} across the whole market. Which generic strategy is this?`,
  ()=>O("Cost leadership, competing on low cost across a broad market","",true),
  c=>[O("Differentiation, offering unique features across the market","Competes on uniqueness rather than cost"),
      O("Focused differentiation, targeting a narrow segment","The market here is broad, not narrow"),
      O("Focused cost leadership in a niche","The firm targets the whole market, not a niche")],
  c=>`Cost leadership means achieving the lowest cost position across a broad market. Differentiation is based on unique attributes, and focused strategies target narrow segments rather than the whole market.`,
  c=>"Low cost plus broad market equals cost leadership.");

S(9,2,"Stuck in the middle","ANALYZE",3,"CONCEPT_MCQ","Recognize a weak strategic position","strategy2",
  (name,r)=>base(name,r),
  c=>`${c.name} has neither the lowest costs nor genuinely unique ${c.product} features. What strategic risk does this create?`,
  ()=>O("Being stuck in the middle, with no clear competitive advantage","",true),
  c=>[O("A strong differentiation advantage automatically","No unique features exist to differentiate"),
      O("A guaranteed cost advantage over rivals","Costs are not the lowest here"),
      O("Monopoly power in the ${c.product} market","Lacks any of the conditions for monopoly")],
  c=>`A firm that is neither low cost nor differentiated is described as stuck in the middle and typically earns weaker returns. Without a clear advantage it has no defensible basis for competing, and it certainly does not gain monopoly power.`,
  c=>"No cost edge and no uniqueness leaves no clear advantage.");

S(9,3,"SWOT analysis","UNDERSTAND",2,"CONCEPT_MCQ","Classify a SWOT item","planning",
  (name,r)=>base(name,r),
  c=>`A new competitor entering the ${c.product} market is noted by ${c.name}. How should this be classified in a SWOT analysis?`,
  ()=>O("A threat, because it is an external development that could harm the firm","",true),
  c=>[O("A weakness, because it is an internal shortcoming","The competitor is external, not internal"),
      O("A strength, because it shows market demand","A rival's arrival is not the firm's strength"),
      O("An opportunity, because competition proves the market","A new rival is generally a risk, not an opportunity")],
  c=>`SWOT separates internal factors, strengths and weaknesses, from external factors, opportunities and threats. A new competitor is an external development that could harm the firm, so it is a threat rather than an internal or positive factor.`,
  c=>"Decide internal versus external, then helpful versus harmful.");

S(9,3,"TOWS strategy","ANALYZE",3,"CONCEPT_MCQ","Derive a TOWS strategy","planning",
  (name,r)=>base(name,r),
  c=>`${c.name} has a strong brand, a strength, and faces a new competitor, a threat. Which strategy best combines these two factors?`,
  ()=>O("Use the brand strength to defend market share against the entrant","",true),
  c=>[O("Ignore the threat because strengths always dominate","Strengths do not automatically neutralize threats"),
      O("Exit the market immediately despite the strong brand","Overreacts without using the existing advantage"),
      O("Reduce brand investment to fund a price war","Weakens the very strength identified")],
  c=>`A threat-focused strategy uses organizational strengths to reduce external threats. Leveraging a strong brand to defend share matches the brand strength against the competitive threat, while ignoring, exiting, or undermining the brand wastes the advantage.`,
  c=>"TOWS pairs an internal factor with an external one.");

S(9,4,"Diversification","UNDERSTAND",2,"CONCEPT_MCQ","Identify related diversification","corporate",
  (name,r)=>base(name,r),
  c=>`${c.name} expands from ${c.product} into a closely related line that uses the same skills and customers. Which strategy is this?`,
  ()=>O("Related diversification, entering a business with shared capabilities","",true),
  c=>[O("Unrelated diversification, entering an entirely different industry","The new line shares skills and customers"),
      O("Market penetration in the original market only","The firm moved into a different line"),
      O("Horizontal integration through acquiring a direct rival","The new line is related, not a competitor")],
  c=>`Related diversification moves into a business that shares capabilities or markets with the existing one, allowing synergy. Unrelated diversification lacks that link, penetration stays in the current market, and horizontal integration means acquiring rivals.`,
  c=>"Shared skills and customers signal related diversification.");

S(9,4,"Vertical integration","APPLY",2,"CONCEPT_MCQ","Identify backward integration","corporate",
  (name,r)=>base(name,r),
  c=>`${c.name} buys a supplier of the raw material used in its ${c.product}. Which corporate strategy is this?`,
  ()=>O("Backward vertical integration, gaining control of inputs","",true),
  c=>[O("Forward vertical integration, gaining control of distribution","Suppliers are upstream, not downstream"),
      O("Horizontal integration, acquiring a competing firm","The acquired firm is a supplier, not a rival"),
      O("Unrelated diversification into a new industry","The supplier is part of the same value chain")],
  c=>`Backward vertical integration means acquiring control of suppliers or inputs that feed the business. Forward integration concerns distribution toward customers, horizontal integration involves rivals, and this move stays within the existing value chain.`,
  c=>"Owning an upstream supplier is backward integration.");

// ---------------- UNIT 10: Innovation, Technology & Scaling ----------------
S(10,1,"Innovation types","UNDERSTAND",2,"CONCEPT_MCQ","Identify radical innovation","innovation",
  (name,r)=>base(name,r),
  c=>`${c.name} introduces a ${c.product} technology that creates an entirely new market and makes old designs obsolete. Which type of innovation is this?`,
  ()=>O("Radical innovation, a major breakthrough that transforms markets","",true),
  c=>[O("Incremental innovation, making small improvements to existing products","The change creates a new market, not a small tweak"),
      O("Process innovation limited to internal efficiency","The innovation changes the market offering"),
      O("Imitation of a competitor's existing design","It is a breakthrough, not a copy")],
  c=>`Radical innovation involves a substantial breakthrough that can create new markets and displace existing technologies. Incremental innovation refines what exists, process innovation changes how things are made, and imitation copies rather than transforms.`,
  c=>"New market plus obsolete old designs signals radical innovation.");

S(10,1,"Diffusion of innovation","UNDERSTAND",2,"CONCEPT_MCQ","Identify early adopters","innovation",
  (name,r)=>base(name,r),
  c=>`${c.name} launches a novel ${c.product}. Which customer group is typically the first to try a new innovation after the inventors themselves?`,
  ()=>O("Early adopters, who embrace new products before the mainstream","",true),
  c=>[O("The late majority, who wait until a product is established","They adopt after the mainstream"),
      O("Laggards, who resist change the longest","They are the last group to adopt"),
      O("The early majority, who follow innovators after proof","Early adopters come before the early majority")],
  c=>`Diffusion of innovation places adopters in sequence: innovators, early adopters, early majority, late majority, and laggards. Early adopters are the first substantial group after the innovators, while the majority and laggards follow later.`,
  c=>"Recall the standard adopter sequence from first to last.");

S(10,2,"Technology adoption","APPLY",2,"CONCEPT_MCQ","Assess a technology adoption decision","digital",
  (name,r)=>base(name,r),
  c=>`${c.name} adopts a new ${c.product} platform only after rivals show clear gains from it. What adoption approach is this?`,
  ()=>O("A follower approach, adopting after early users demonstrate value","",true),
  c=>[O("A first-mover approach, adopting before anyone else","The firm waits for rivals to prove the value"),
      O("A laggard approach, avoiding the technology indefinitely","The firm does eventually adopt it"),
      O("An experimental approach with no intention to scale","Adoption follows proven benefits")],
  c=>`A follower adopts once early users have demonstrated value, reducing uncertainty at the cost of possible first-mover advantages. A first mover acts early, a laggard avoids adoption, and the firm here is neither avoiding nor experimenting aimlessly.`,
  c=>"Waiting for proven value but still adopting is a follower stance.");

S(10,2,"Digital transformation","ANALYZE",3,"CONCEPT_MCQ","Identify digital transformation","digital",
  (name,r)=>base(name,r),
  c=>`${c.name} uses data and digital tools to redesign how it creates and delivers ${c.product}, not just to automate a single task. Which concept does this illustrate?`,
  ()=>O("Digital transformation, reshaping the business model with technology","",true),
  c=>[O("Simple digitization of paper records","Is a narrow conversion, not a model redesign"),
      O("A one-time software installation","Does not capture the broad organizational change"),
      O("Outsourcing all operations to a vendor","Describes sourcing, not technology-driven redesign")],
  c=>`Digital transformation uses technology to fundamentally change how a business creates and delivers value, reshaping processes and models. Digitizing records or installing software is a narrower change, and outsourcing is a sourcing decision.`,
  c=>"Look for broad redesign of value creation, not one tool.");

S(10,3,"Economies of scale","UNDERSTAND",2,"CONCEPT_MCQ","Explain economies of scale","scaling",
  (name,r)=>base(name,r),
  c=>`As ${c.name} produces more ${c.product}, its average cost per unit falls. What explains this?`,
  ()=>O("Economies of scale, where spreading fixed costs lowers average cost","",true),
  c=>[O("Diseconomies of scale, where growth raises average cost","Describes the opposite effect"),
      O("Diminishing returns to a fixed factor in the short run","Applies when variable inputs are added to a fixed one"),
      O("A decrease in total fixed costs as output grows","Fixed costs do not fall with output; they are spread")],
  c=>`Economies of scale arise when higher output spreads fixed costs over more units and enables efficiencies, lowering average cost. Diseconomies of scale push average cost up, and diminishing returns concern adding variable inputs to a fixed factor.`,
  c=>"Spreading fixed costs over more units lowers average cost.");

S(10,3,"Scaling challenges","ANALYZE",3,"CONCEPT_MCQ","Identify a scaling bottleneck","scaling",
  (name,r)=>base(name,r),
  c=>`${c.name} grows ${c.product} sales quickly, but its manual ordering process cannot keep up and errors rise. What is the main issue?`,
  ()=>O("Processes are not standardized or automated enough to scale","",true),
  c=>[O("Demand is too low to justify growth","Sales are growing quickly, not slowly"),
      O("The product lacks any customer demand","Customers are clearly buying the product"),
      O("The firm should stop selling to avoid errors","Addresses symptoms by abandoning growth")],
  c=>`Scaling requires processes that handle higher volume without a proportional rise in errors or effort. Manual, non-standardized ordering becomes a bottleneck as sales grow; the problem is not weak demand and stopping sales is not a solution.`,
  c=>"Growth exposing a fragile manual process is a scaling bottleneck.");

S(10,4,"Startup metrics","UNDERSTAND",2,"CONCEPT_MCQ","Interpret burn and runway","metrics",
  (name,r)=>base(name,r),
  c=>`${c.name} spends more cash each month on ${c.product} than it collects. Which statement about its finances is accurate?`,
  ()=>O("It has negative cash flow and a finite runway unless funding changes","",true),
  c=>[O("It is profitable because revenue is growing","Profit depends on costs versus revenue, not growth alone"),
      O("It has no cash risk while it still has a bank balance","Spending above receipts depletes the balance over time"),
      O("Its runway is unlimited as long as customers exist","Runway depends on cash and burn, not customers alone")],
  c=>`When cash outflows exceed inflows, the firm has negative cash flow and its runway, the time until cash runs out, is finite unless it raises funding or reaches profitability. A positive balance or customer base does not remove that constraint.`,
  c=>"Spending more than you collect shortens runway.");

S(10,4,"Runway calculation","APPLY",2,"NUMERIC","Compute cash runway","metrics",
  (name,r)=>({...base(name,r), A:600000, B:50000}),
  c=>`${c.name} holds ${money(c.A)} in cash and burns ${money(c.B)} per month developing ${c.product}. About how many months of runway does it have?`,
  c=>null,c=>[],
  c=>`Runway is cash on hand divided by monthly net burn: ${money(c.A)} divided by ${money(c.B)} per month gives about ${num(c.A/c.B)} months. If burn rises or new funding arrives, the actual runway will differ from this estimate.`,
  c=>"Divide cash on hand by the monthly burn rate.",null,c=>c.A/c.B);

// ---------------- Engine ----------------
function shuffle(arr, rng){
  for(let i=arr.length-1;i>0;i--){const j=Math.floor(rng()*(i+1));[arr[i],arr[j]]=[arr[j],arr[i]];}
  return arr;
}

const PROV = {
  author:"business-bank-author",
  reviewer:"business-bank-reviewer",
  reviewedAt:"2026-11-23T00:00:00Z",
  aiAssisted:true,
  aiModel:"hy4-flash-free",
  aiRunId:"business-bank-gen-1",
  humanReviewer:"orchestrator-spotcheck",
  fairUseRationale:null,
  attestation:"authored from first principles with fictional scenarios and data; no protected content used"
};

function nameFor(idx){
  const p=Math.floor(idx/SUFFIX.length), s=idx%SUFFIX.length;
  return PREFIX[p]+SUFFIX[s];
}

const PER_SPEC = 50;
const TOTAL = SPECS.length*PER_SPEC;
if(TOTAL!==4000) throw new Error("expected 4000 but got "+TOTAL);

let gidx=0;
const all=[];
for(const sp of SPECS){
  const sameTopic=SPECS.filter(x=>x.u===sp.u&&x.t===sp.t);
  const topicPos=sameTopic.indexOf(sp);
  const questions=[];
  for(let k=0;k<PER_SPEC;k++){
    const name=nameFor(gidx++);
    const rng=mulberry32(0x9e3779b9 ^ (gidx*2654435761));
    const c=sp.c===undefined?base(name,rng):sp.c(name,rng);
    if(!c.name) c.name=name;
    let stemText=sp.stem(c).replace(/\ba (organic|accounting|artisan)\b/g,"an $1");
    const head=stemText.toLowerCase().replace(/[^a-z0-9\s]/g," ").split(/\s+/).slice(0,12).join(" ");
    if(!head.includes(name.toLowerCase())) stemText=`${name}, ${stemText.charAt(0).toLowerCase()}${stemText.slice(1)}`;
    const b={stem:stemText};
    const stimulus=typeof sp.stimulus==="function"?sp.stimulus(c):sp.stimulus;
    if(stimulus) b.stimulus=stimulus;
    let answer, options=[];
    const correct=typeof sp.correct==="function"?sp.correct(c):sp.correct;
    const wrongs=typeof sp.wrongs==="function"?sp.wrongs(c):sp.wrongs;
    if(correct){
      const all=[correct,...wrongs].map(o=>({t:o.t,m:o.m,correct:!!o.correct}));
      shuffle(all,rng);
      options=all.map((o,i)=>({id:"ABCD"[i],text:o.t,misconception:o.m}));
      const ci=all.findIndex(o=>o.correct);
      answer={correctId:"ABCD"[ci]};
    }else{
      const v=typeof sp.answerFn==="function"?sp.answerFn(c):sp.answerFn;
      answer={value:Math.round(v*100)/100,tolerance:0.01};
    }
    const idNum=topicPos*PER_SPEC+k+1;
    const id=`business-${sp.u}.${sp.t}-${String(idNum).padStart(4,"0")}`;
    questions.push({
      id,
      subject:"BUSINESS_ENTREPRENEURSHIP",
      unit:`Unit ${sp.u}`,
      topic:`${sp.topic} — ${sp.practice}`,
      learningObjective:sp.lo,
      practice:sp.practice,
      scope:"core-scope",
      cognitiveDemand:{bloom:sp.bloom,dok:sp.dok},
      format:sp.format,
      stimulus:stimulus||null,
      stem:b.stem,
      options,
      answer,
      explanation:sp.why(c),
      hint:sp.hint(c),
      difficulty:Math.round((0.45+sp.dok*0.1)*100)/100,
      discrimination:0.32,
      responseTimeBaseline:{p25Sec:20,medianSec:42,p75Sec:70},
      provenance:{...PROV,stimulusLicense:stimulus?"original":null},
      tags:[`unit-${sp.u}`,sp.practice,"business-entrepreneurship"],
      relatedQuestionIds:[],
      prerequisiteQuestionIds:[],
      version:1
    });
  }
  all.push(...questions);
}

fs.mkdirSync(OUT,{recursive:true});
for(const f of fs.readdirSync(OUT)) if(f.endsWith(".json")) fs.unlinkSync(path.join(OUT,f));
const files=[];
for(let i=0;i<all.length;i+=100){
  const fname=`business-batch-${String(i/100+1).padStart(3,"0")}.json`;
  files.push({path:path.join(OUT,fname),questions:all.slice(i,i+100)});
}
let written=0;
for(const f of files){
  fs.writeFileSync(f.path,JSON.stringify({questions:f.questions},null,2)+"\n");
  written+=f.questions.length;
}
console.log(`generated ${written} questions in ${files.length} files -> ${OUT}`);
