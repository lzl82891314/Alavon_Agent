update model_profile
set protocol = 'OPENAI_COMPATIBLE_CHAT'
where protocol = 'OPENAI_CHAT_COMPLETIONS';
