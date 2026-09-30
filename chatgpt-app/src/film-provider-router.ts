export type FilmProviderCapability={
  id:string;
  available:boolean;
  rights_ok:boolean;
  privacy_ok:boolean;
  paid:boolean;
  max_duration_sec:number;
  max_resolution_rank:number;
  image_reference:boolean;
  video_reference:boolean;
  audio_generation:boolean;
  lip_sync:boolean;
  camera_control:boolean;
  multi_shot:boolean;
  identity_continuity:number;
  motion_quality:number;
  camera_quality:number;
  audio_quality:number;
  latency_score:number;
  cost_score:number;
  verified:boolean;
};

export type FilmShotRequirement={
  duration_sec:number;
  min_resolution_rank:number;
  image_reference?:boolean;
  video_reference?:boolean;
  audio_generation?:boolean;
  lip_sync?:boolean;
  camera_control?:boolean;
  multi_shot?:boolean;
};

function supports(p:FilmProviderCapability,r:FilmShotRequirement){
  if(!p.available||!p.rights_ok||!p.privacy_ok||!p.verified) return false;
  if(p.paid) return false;
  if(p.max_duration_sec<r.duration_sec) return false;
  if(p.max_resolution_rank<r.min_resolution_rank) return false;
  if(r.image_reference&&!p.image_reference) return false;
  if(r.video_reference&&!p.video_reference) return false;
  if(r.audio_generation&&!p.audio_generation) return false;
  if(r.lip_sync&&!p.lip_sync) return false;
  if(r.camera_control&&!p.camera_control) return false;
  if(r.multi_shot&&!p.multi_shot) return false;
  return true;
}

export function routeFilmShot(
  requirement:FilmShotRequirement,
  providers:FilmProviderCapability[]
){
  const eligible=providers.filter(p=>supports(p,requirement));
  const ranked=eligible.map(p=>{
    const score=
      p.identity_continuity*5+
      p.motion_quality*5+
      p.camera_quality*4+
      p.audio_quality*(requirement.audio_generation||requirement.lip_sync?4:1)+
      p.latency_score+
      p.cost_score*2;
    return {provider:p,score};
  }).sort((a,b)=>b.score-a.score||a.provider.id.localeCompare(b.provider.id));

  if(!ranked.length){
    return {
      ok:false,
      provider:null,
      reason:"no_verified_provider_satisfies_hard_gates"
    };
  }
  return {
    ok:true,
    provider:ranked[0]!.provider,
    score:ranked[0]!.score,
    candidates:ranked.map(x=>({id:x.provider.id,score:x.score}))
  };
}
