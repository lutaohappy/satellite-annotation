#!/bin/bash
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no ../backend/target/*.jar root@192.168.200.77:/opt/
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no -r ../frontend/dist root@192.168.200.77:/opt/html/
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no nginx.conf root@192.168.200.77:/etc/nginx/
sshpass -p 'box@gridknow' scp -o StrictHostKeyChecking=no deploy.sh root@192.168.200.77:/opt/
echo "文件上传完成"
