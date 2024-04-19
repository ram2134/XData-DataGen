delete from prereq
delete from takes
delete from teaches
delete from section
delete from course
delete from advisor
delete from instructor
delete from student
delete from department
delete from classroom
insert into classroom values ('Packard','120','272')
insert into classroom values ('Taylor','3128','61')
insert into classroom values ('Packard','3128','117')
insert into department values ('V9','V7','81073.7445')
insert into department values ('Music','Taylor','65106.7445')
insert into department values ('Biology','Packard','65974.0')
insert into department values ('Finance','Painter','53142.0')
insert into department values ('V11','V6','54263.0')
insert into department values ('Comp. Sci.','Taylor','65105.7445')
insert into department values ('V8','Watson','108555.0')
insert into department values ('Physics','V5','62572.0')
insert into department values ('Elec. Eng.','Taylor','118047.7445')
insert into department values ('V10','Taylor','53143.0')
insert into instructor values ('10101','Einstein',null,'40000.0')
insert into instructor values ('10101','Einstein',null,'40000.0')
insert into instructor values ('12121','Einstein','V11','94791.0')
insert into instructor values ('15151','Einstein','V8',null)
insert into course values ('CS-347','Robotics',null,'4')
insert into course values ('CS-347','Robotics',null,'4')
insert into course values ('CS-347','Robotics',null,'4')
insert into course values ('BIO-301','Music Video Production',null,'4')
insert into course values ('CS-347','Robotics',null,'4')
insert into course values ('BIO-301','Music Video Production',null,'4')
insert into section values ('CS-347','1','Summer','2010','Packard',null,'B')
insert into section values ('BIO-301','2','Spring','2009','Taylor','3128','B')
insert into section values ('CS-347','1','Summer','2010','Packard',null,'B')
insert into teaches values ('10101','CS-347','1','Summer','2010')
insert into teaches values ('12121','BIO-301','2','Spring','2009')
insert into teaches values ('10101','CS-347','1','Summer','2010')
