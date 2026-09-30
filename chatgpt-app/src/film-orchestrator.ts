export type FilmStage=
  |"VISION_LOCKED"
  |"WORLD_BIBLE_LOCKED"
  |"CHARACTER_BIBLE_LOCKED"
  |"SCREENPLAY_LOCKED"
  |"STORYBOARD_READY"
  |"SHOT_CONTRACTS_READY"
  |"ROUTED"
  |"RENDERING"
  |"SHOT_QA"
  |"EDITING"
  |"FILM_QA"
  |"MASTERING"
  |"DELIVERED";

export type FilmEvidence={
  world_bible_locked?:boolean;
  character_bible_locked?:boolean;
  screenplay_locked?:boolean;
  storyboard_ready?:boolean;
  shot_contracts_ready?:boolean;
  provider_routes_complete?:boolean;
  rendered_shots_count?:number;
  expected_shots_count?:number;
  all_shot_gates_passed?:boolean;
  timeline_rendered?:boolean;
  all_film_gates_passed?:boolean;
  master_created?:boolean;
  full_playback_passed?:boolean;
  artifact_sha256?:string;
  delivered_sha256?:string;
  tested_sha256?:string;
};

const ORDER:FilmStage[]=[
  "VISION_LOCKED",
  "WORLD_BIBLE_LOCKED",
  "CHARACTER_BIBLE_LOCKED",
  "SCREENPLAY_LOCKED",
  "STORYBOARD_READY",
  "SHOT_CONTRACTS_READY",
  "ROUTED",
  "RENDERING",
  "SHOT_QA",
  "EDITING",
  "FILM_QA",
  "MASTERING",
  "DELIVERED"
];

function sha(value:unknown){
  return typeof value==="string"&&/^[0-9a-f]{64}$/i.test(value)?value.toLowerCase():null;
}

function gateFor(target:FilmStage,e:FilmEvidence){
  switch(target){
    case "WORLD_BIBLE_LOCKED": return e.world_bible_locked===true;
    case "CHARACTER_BIBLE_LOCKED": return e.character_bible_locked===true;
    case "SCREENPLAY_LOCKED": return e.screenplay_locked===true;
    case "STORYBOARD_READY": return e.storyboard_ready===true;
    case "SHOT_CONTRACTS_READY": return e.shot_contracts_ready===true;
    case "ROUTED": return e.provider_routes_complete===true;
    case "RENDERING": return true;
    case "SHOT_QA":
      return Number.isInteger(e.rendered_shots_count)&&
        Number.isInteger(e.expected_shots_count)&&
        (e.expected_shots_count??0)>0&&
        e.rendered_shots_count===e.expected_shots_count;
    case "EDITING": return e.all_shot_gates_passed===true;
    case "FILM_QA": return e.timeline_rendered===true;
    case "MASTERING": return e.all_film_gates_passed===true;
    case "DELIVERED":{
      const artifact=sha(e.artifact_sha256);
      const tested=sha(e.tested_sha256);
      const delivered=sha(e.delivered_sha256);
      return e.master_created===true&&
        e.full_playback_passed===true&&
        artifact!==null&&tested!==null&&delivered!==null&&
        artifact===tested&&tested===delivered;
    }
    case "VISION_LOCKED": return false;
  }
}

export function nextFilmStage(current:FilmStage){
  const i=ORDER.indexOf(current);
  if(i<0||i>=ORDER.length-1) return null;
  return ORDER[i+1]!;
}

export function evaluateFilmTransition(
  current:FilmStage,
  target:FilmStage,
  evidence:FilmEvidence={}
){
  const currentIndex=ORDER.indexOf(current);
  const targetIndex=ORDER.indexOf(target);
  if(currentIndex<0||targetIndex<0){
    return {ok:false,reason:"invalid_stage",current,target};
  }
  if(targetIndex!==currentIndex+1){
    return {
      ok:false,
      reason:"only_next_stage_transition_allowed",
      current,target,
      expected:nextFilmStage(current)
    };
  }
  if(!gateFor(target,evidence)){
    return {
      ok:false,
      reason:"required_evidence_missing_or_failed",
      current,target,
      expected:target
    };
  }
  return {ok:true,current,target};
}

export function assertFilmTransition(
  current:FilmStage,
  target:FilmStage,
  evidence:FilmEvidence={}
){
  const result=evaluateFilmTransition(current,target,evidence);
  if(!result.ok) throw new Error("film_transition_blocked:"+result.reason);
  return result;
}
