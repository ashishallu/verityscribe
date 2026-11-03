-- New appointment modes; preserve existing enum labels and rows.
alter type public.consultation_type_enum add value if not exists 'ai_chat';
alter type public.consultation_type_enum add value if not exists 'ai_virtual';
