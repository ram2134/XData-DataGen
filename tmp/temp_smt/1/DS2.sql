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
insert into classroom values ('Taylor','3128','170')
insert into classroom values ('Packard','120','10')
insert into department values ('Biology','Taylor','50000.0')
insert into department values ('Comp. Sci.','Painter',null)
insert into department values ('Finance','Taylor','65974.0')
insert into department values ('History','Watson','111985.0')
insert into department values ('Elec. Eng.','Taylor','108555.0')
insert into department values ('Music','Taylor','62572.0')
insert into department values ('Biology','Taylor','50000.0')
insert into department values ('Physics','Packard','67059.0')
insert into instructor values ('15151','Einstein','History','46798.8764')
insert into instructor values ('10101','Einstein',null,'46797.8764')
insert into instructor values ('12121','Einstein','Finance','93454.5')
insert into instructor values ('10101','Einstein',null,'46797.8764')
insert into course values ('BIO-301','Robotics','Biology','4')
insert into course values ('CS-347','Music Video Production',null,'3')
insert into course values ('BIO-301','Robotics','Biology','4')
insert into course values ('CS-347','Music Video Production',null,'3')
insert into section values ('BIO-301','2','Summer','2010','Taylor','3128','B')
insert into section values ('CS-347','1','Spring','2009','Packard',null,'C')
insert into teaches values ('12121','BIO-301','2','Summer','2010')
insert into teaches values ('10101','CS-347','1','Spring','2009')
